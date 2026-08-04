"""LangGraph 状态机骨架（09 ticket，CONTEXT §5）。

StateGraph + 条件边：router → 各意图节点 → 汇聚（reply）。
09 预留全部意图节点插槽，11-15 只填空不改骨架。
"""

from typing import Any, Callable, TypedDict

from langgraph.graph import END, StateGraph

from app.intents import INTENTS, build_intent_node, build_router


class AgentState(TypedDict):
    """图状态：消息列表 + 路由结果 + 最终回复 + 工具调用轨迹。"""

    messages: list[dict[str, str]]
    intent: str
    reply: str
    tool_calls: list[dict[str, str]]


def build_graph(router: Callable | None = None, llm: Any = None) -> Any:
    """构建并编译状态机。router 可注入（测试用 mock；默认真实 LLM 路由在此一次性装配）。
    llm 可注入（triage 等意图节点用；默认由 build_router_from_llm 装配）。"""
    # router 只装配一次：真实模式由 get_graph 缓存（main.py），避免每次 invoke 重建 LLM 客户端
    route = router if router is not None else build_router_from_llm()
    # llm 只装配一次（同 router 策略）
    resolved_llm = llm if llm is not None else build_llm_if_needed()
    state_graph = StateGraph(AgentState)

    async def route_node(state: AgentState) -> dict[str, Any]:
        return {"intent": await route(state["messages"])}

    # 确保 tool_calls 在初始状态中有默认值
    state_graph.add_node("router", route_node)
    for intent in INTENTS:
        state_graph.add_node(intent, build_intent_node(intent, llm=resolved_llm))

    state_graph.set_entry_point("router")
    state_graph.add_conditional_edges(
        "router",
        lambda s: s["intent"],
        {intent: intent for intent in INTENTS},
    )
    for intent in INTENTS:
        state_graph.add_edge(intent, END)

    return state_graph.compile()


def build_router_from_llm() -> Callable:
    """装配真实 LLM 路由（延迟导入避免测试无 LLM 配置时加载失败）。"""
    from app.llm import build_chat_model

    return build_router(build_chat_model())


def build_llm_if_needed() -> Any:
    """按需装配 LLM 实例（triage 节点等需要 LLM 的意图使用）。
    延迟导入，避免测试环境无 LLM 配置时报错。
    echo 模式下返回 None（triage 节点降级为 mock 回复）。"""
    from app.config import get_settings

    settings = get_settings()
    if settings.agent_echo_mode:
        return None
    try:
        from app.llm import build_chat_model

        return build_chat_model()
    except Exception as e:
        raise RuntimeError(f"LLM 初始化失败: {e}") from e
