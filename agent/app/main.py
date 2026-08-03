"""SmartMed Agent HTTP 服务（09 ticket，CONTEXT §5/§8，ADR-0014）。

POST /agent/chat：
- 校验 X-Agent-Secret（双向鉴权，与 Java 同一密钥）
- echo 模式（AGENT_ECHO_MODE=true）：绕过 LLM，回显最后一条用户消息（链路 smoke）
- 真实模式：LangGraph 状态机 invoke（真实 LLM 意图分类 + mock 意图节点回复），
  回复文本按块输出为 SSE delta 事件（模拟逐字流式，真实 token 流留给 11-15）
- 异常兜底输出 error 事件（Java 转发失败走 HTTP 502，见 CONTEXT §8）

启动：cd agent && uvicorn app.main:app --port 8000
"""

import asyncio
import logging
from contextlib import asynccontextmanager
from functools import lru_cache
from typing import AsyncIterator, Literal

from fastapi import Depends, FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from app.config import get_settings
from app.graph import AgentState, build_graph
from app.security import require_agent_secret
from app.sse import delta_event, done_event, error_event

logger = logging.getLogger(__name__)

# mock 回复按块输出的大小与间隔（模拟逐字流式，验证 SSE 链路）
_CHUNK_SIZE = 4
_CHUNK_DELAY = 0.01


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    """无状态请求体：全量历史消息（CONTEXT §8），Java 注入 X-Patient-Id。"""

    messages: list[ChatMessage]


@asynccontextmanager
async def lifespan(_app: FastAPI):
    """启动连通性校验：非 echo 模式下 LLM 不可用即清晰报错阻断启动。"""
    settings = get_settings()
    if not settings.agent_echo_mode:
        from app.llm import check_llm_connectivity

        await check_llm_connectivity()
    yield


app = FastAPI(title="SmartMed Agent", version="0.1.0", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/agent/chat")
async def chat(req: ChatRequest, _auth: None = Depends(require_agent_secret)) -> StreamingResponse:
    """对话入口：校验 secret 后按模式输出 SSE 事件流。"""
    settings = get_settings()
    if settings.agent_echo_mode:
        return StreamingResponse(echo_stream(req), media_type="text/event-stream")
    return StreamingResponse(agent_stream(req), media_type="text/event-stream")


async def echo_stream(req: ChatRequest) -> AsyncIterator[str]:
    """echo 模式：回显最后一条用户消息为 delta 事件（无 LLM，链路 smoke）。

    assistant 消息不回显（避免把历史回答再回显一遍，只验证用户输入链路）。
    """
    last_user = next((m.content for m in reversed(req.messages) if m.role == "user"), "")
    for chunk in _chunks(last_user):
        yield delta_event(chunk)
        await asyncio.sleep(_CHUNK_DELAY)
    yield done_event()


async def agent_stream(req: ChatRequest) -> AsyncIterator[str]:
    """真实模式：LangGraph 状态机 → mock 意图回复 → 分块 delta。异常兜底 error 事件。"""
    try:
        graph = get_graph()
        state = await graph.ainvoke(AgentState(
            messages=[{"role": m.role, "content": m.content} for m in req.messages],
            intent="",
            reply="",
        ))
        reply = state.get("reply") or ""
        for chunk in _chunks(reply):
            yield delta_event(chunk)
            await asyncio.sleep(_CHUNK_DELAY)
        yield done_event()
    except Exception as e:  # noqa: BLE001 —— 流式响应中途异常只能以 error 事件收尾
        logger.error("agent 处理失败", exc_info=e)
        yield error_event("服务暂时不可用，请稍后重试")


@lru_cache
def get_graph():
    """图实例缓存：router 首次装配真实 LLM（build_router_from_llm 内延迟导入）。"""
    return build_graph()


def _chunks(text: str) -> list[str]:
    return [text[i:i + _CHUNK_SIZE] for i in range(0, len(text), _CHUNK_SIZE)]
