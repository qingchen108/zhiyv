"""LangGraph 状态机骨架（09 ticket，CONTEXT §5）。

StateGraph + 条件边：router → 各意图节点 → 汇聚（reply）。
09 预留全部意图节点插槽，11-15 只填空不改骨架。
"""

from typing import Any, Callable, TypedDict

from langgraph.graph import END, StateGraph

from app.intents import INTENTS, build_intent_node, build_router


class AgentState(TypedDict):
    """图状态：消息列表 + 路由结果 + 最终回复。"""

    messages: list[dict[str, str]]
    intent: str
    reply: str


def build_graph(router: Callable | None = None) -> Any:
    """构建并编译状态机。router 可注入（测试用 mock；默认真实 LLM 路由在此一次性装配）。"""
    # router 只装配一次：真实模式由 get_graph 缓存（main.py），避免每次 invoke 重建 LLM 客户端
    route = router if router is not None else build_router_from_llm()
    state_graph = StateGraph(AgentState)

    async def route_node(state: AgentState) -> dict[str, Any]:
        return {"intent": await route(state["messages"])}

    state_graph.add_node("router", route_node)
    for intent in INTENTS:
        state_graph.add_node(intent, build_intent_node(intent))

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
