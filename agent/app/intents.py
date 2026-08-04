"""意图集与意图路由（09 ticket，CONTEXT §5）。

6 类意图与 ticket 11-14 能力域一一对应；emotion 是旁路语气能力，不构成意图（领域模型见 CONTEXT 术语表）。
09 阶段：router 节点真实 LLM 分类（验证分类质量），各意图节点仅返回 mock 回复并预留工具插槽。
"""

import logging
from typing import Any, Awaitable, Callable, Literal

from langchain_core.language_models.chat_models import BaseChatModel

logger = logging.getLogger(__name__)

Intent = Literal["triage", "registration", "consultation", "pharmacy", "reminder", "general"]

INTENTS: list[str] = ["triage", "registration", "consultation", "pharmacy", "reminder", "general"]

# 意图中文标签（进 prompt，让 LLM 分类更稳）
INTENT_LABELS: dict[str, str] = {
    "triage": "导诊（描述症状/找科室/找医生）",
    "registration": "挂号（查排班/约号/挂号）",
    "consultation": "预问诊与处方解读（看病情摘要/解读处方/过敏提示）",
    "pharmacy": "购药（买药/药店对比/下单）",
    "reminder": "用药提醒（设置服药提醒）",
    "general": "闲聊或以上皆非（问候/寒暄/其他）",
}

_INTENT_LIST_PROMPT = "、".join(f"{k}（{v}）" for k, v in INTENT_LABELS.items())

# 各意图节点的 mock 回复（09 骨架占位，11-15 替换为真实编排）
MOCK_REPLIES: dict[str, str] = {
    "triage": "（导诊骨架）我可以帮您分析症状、推荐科室和医生。这个能力将在后续版本接通知识图谱，请先让我了解您的症状。",
    "registration": "（挂号骨架）我可以帮您查询排班并创建挂号。这个能力将在后续版本接通号源系统。",
    "consultation": "（预问诊骨架）我可以帮您整理病情摘要、解读处方。这个能力将在后续版本接通病历系统。",
    "pharmacy": "（购药骨架）我可以帮您对比药店并创建购药单。这个能力将在后续版本接通药店库存。",
    "reminder": "（用药提醒骨架）我可以帮您设置用药提醒。这个能力将在后续版本接通提醒系统。",
    "general": "我是智愈健康助手，可以帮您导诊、挂号、解读处方、购药和设置用药提醒。请问有什么可以帮您？",
}


def _extract_intent(content: Any) -> str:
    """从 LLM 响应提取意图词，兼容 str 与 thinking 模型块列表。

    doubao-seed-2.1-turbo 是 thinking 模型：content 为 [{'type': 'thinking', ...},
    {'type': 'text', 'text': 'triage'}] 块列表；非 thinking 模型为纯字符串。
    统一取 text 块（或字符串本身）后做包含匹配，未命中返回 general。
    """
    if isinstance(content, str):
        text = content
    elif isinstance(content, list):
        text = " ".join(
            block.get("text", "") if isinstance(block, dict) else str(block)
            for block in content
        )
    else:
        text = str(content)
    lowered = text.lower()
    for intent in INTENTS:
        if intent in lowered:
            return intent
    logger.warning("意图分类结果无法解析，兜底 general: content=%r", content)
    return "general"


def build_router(llm: BaseChatModel) -> Callable[[list[dict[str, str]]], Awaitable[str]]:
    """构建意图路由节点：真实 LLM 分类，失败兜底 general。

    返回 async 函数：输入消息列表，输出意图名。分类失败（网络/解析）记录日志并兜底 general，
    不让一次分类失败拖垮整个对话。
    火山方舟 coding 端点不支持结构化输出（tool calling 不稳定 / json_schema 无效），
    故用 prompt 要求输出意图词 + 本地匹配解析。
    """

    async def route(messages: list[dict[str, str]]) -> str:
        try:
            res = await llm.ainvoke([
                {
                    "role": "system",
                    "content": (
                        "你是智愈医疗助手的意图分类器。只输出一个英文意图词，不要输出其他内容：\n"
                        f"{_INTENT_LIST_PROMPT}\n"
                        "分类依据用户最新消息的诉求，历史消息仅供参考。"
                    ),
                },
                *messages,
            ])
            return _extract_intent(res.content)
        except Exception as e:  # noqa: BLE001 —— 分类失败兜底 general
            logger.warning("意图分类失败，兜底 general: %s", e)
            return "general"

    return route


def build_intent_node(intent: str, llm: BaseChatModel | None = None) -> Callable[[dict[str, Any]], dict[str, Any]]:
    """构建意图节点。

    11-15 在此节点内接入真实编排：调用工具（LangChain tools，契约 tools.json 单一来源）、
    产出 tool_call / card 事件。节点返回值结构保持 {"reply": str}，主流程按 SSE 协议逐块输出。

    Args:
        intent: 意图名
        llm: LLM 实例（triage 等需要 LLM 的节点使用；未传入时由 get_graph 的 build_router_from_llm 装配）
    """

    # triage 意图使用真实编排（ticket 11），需 LLM；echo 模式或无 LLM 时降级为 mock 回复
    if intent == "triage" and llm is not None:
        from app.triage import build_triage_node

        return build_triage_node(llm)

    def node(state: dict[str, Any]) -> dict[str, Any]:
        # TODO(11-15): 意图 {intent} 的真实编排：工具调用链 + card 事件，见 agent/tools/tools.json
        return {"reply": MOCK_REPLIES[intent], "tool_calls": state.get("tool_calls") or []}

    return node
