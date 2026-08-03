"""LLM 接入层（09 ticket，CONTEXT §5）。

统一 OpenAI-compatible 接口：ChatOpenAI + base_url/api_key/model 三变量切换，
不引入各家 SDK；不做 fallback（演示项目无高可用需求）。
"""

import logging
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI

from app.config import get_settings

logger = logging.getLogger(__name__)

# 启动连通性校验的探针请求（max_tokens=1 的极小调用，仅验证网络 + 密钥可用）
_PROBE_MESSAGE = [{"role": "user", "content": "ping"}]


def build_chat_model() -> BaseChatModel:
    """按环境变量构建 ChatOpenAI 实例（LLM_BASE_URL / LLM_API_KEY / LLM_MODEL 三变量切换）。"""
    settings = get_settings()
    if not settings.llm_base_url or not settings.llm_api_key or not settings.llm_model:
        raise RuntimeError(
            "LLM 未配置完整：LLM_BASE_URL / LLM_API_KEY / LLM_MODEL 三者必填。"
            "请复制 agent/.env.example 为 agent/.env 后填入真实值（AGENT_ECHO_MODE=true 可跳过）"
        )
    return ChatOpenAI(
        model=settings.llm_model,
        api_key=settings.llm_api_key,
        base_url=settings.llm_base_url,
        temperature=0,
        timeout=60,
    )


async def check_llm_connectivity() -> None:
    """启动连通性校验：发一次 max_tokens=1 的探针请求，失败抛清晰报错阻断启动。

    仅非 echo 模式调用；echo 模式（AGENT_ECHO_MODE=true）不依赖 LLM，跳过校验。
    """
    llm = build_chat_model()
    probe = llm.bind(max_tokens=1)
    try:
        await probe.ainvoke(_PROBE_MESSAGE)
        logger.info("LLM 连通性校验通过: base_url=%s model=%s", get_settings().llm_base_url, get_settings().llm_model)
    except Exception as e:  # noqa: BLE001 —— 启动校验需捕获一切失败并给出定位提示
        raise RuntimeError(
            "LLM 连通性校验失败，请检查 agent/.env 的 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL："
            f"{e}"
        ) from e


def convert_messages(messages: list[dict[str, Any]]) -> list[dict[str, str]]:
    """把 HTTP 请求体的消息转换为 LangChain 消息（仅保留 role/content）。"""
    return [{"role": m["role"], "content": m["content"]} for m in messages]
