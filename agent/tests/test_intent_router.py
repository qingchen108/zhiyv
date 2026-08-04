"""意图路由测试（09 ticket）。

- 注入 fake router：验证 StateGraph 条件边把每个意图路由到对应节点并返回 mock 回复
- 真实 router 构建：验证意图词解析（str 与 thinking 块列表）、未知意图兜底 general、LLM 异常兜底 general
"""

import pytest
from types import SimpleNamespace

from app.graph import AgentState, build_graph
from app.intents import INTENTS, MOCK_REPLIES, build_intent_node, build_router

MESSAGES = [{"role": "user", "content": "我头疼"}]


def make_fake_router(intent: str):
    async def route(_messages):
        return intent

    return route


@pytest.mark.parametrize("intent", INTENTS)
async def test_graph_routes_to_correct_intent_node(intent):
    """每个意图都有一条条件边 -> 对应节点。

    registration 节点使用真实编排（ticket 12），调用 Java 工具会因不可达而返回错误提示；
    consultation 节点使用真实编排（ticket 13），无 LLM 时降级为框架回复（非 mock 原文）；
    其他意图仍为 mock 回复。
    """
    graph = build_graph(router=make_fake_router(intent))
    state = await graph.ainvoke(AgentState(messages=MESSAGES, intent="", reply=""))
    assert state["intent"] == intent
    if intent == "registration":
        # registration 节点为真实编排（无 mock 回复），验证它返回了工具调用
        assert "tool_calls" in state
        assert "reply" in state
    elif intent == "consultation":
        # consultation 节点为真实编排（ticket 13），无 LLM 时降级为框架回复，非 mock 原文
        assert "reply" in state
        assert state["reply"] != MOCK_REPLIES["consultation"]
    else:
        assert state["reply"] == MOCK_REPLIES[intent]


class FakeLLM:
    """模拟 LLM：ainvoke 由子类决定（路由只用 ainvoke + content）。"""

    pass


class ReturningLLM(FakeLLM):
    def __init__(self, content):
        self._content = content

    async def ainvoke(self, _messages):
        return SimpleNamespace(content=self._content)


class BrokenLLM(FakeLLM):
    async def ainvoke(self, _messages):
        raise RuntimeError("network down")


async def test_router_returns_parsed_intent():
    router = build_router(ReturningLLM("triage"))
    assert await router(MESSAGES) == "triage"


async def test_router_parses_thinking_block_list():
    """thinking 模型返回块列表时，从 text 块提取意图词。"""
    content = [
        {"type": "thinking", "thinking": "用户想挂号", "signature": "sig"},
        {"type": "text", "text": "registration"},
    ]
    router = build_router(ReturningLLM(content))
    assert await router(MESSAGES) == "registration"


async def test_router_unknown_intent_falls_back_to_general():
    """LLM 返回未定义意图词时兜底 general。"""
    router = build_router(ReturningLLM("hacking"))
    assert await router(MESSAGES) == "general"


async def test_router_llm_failure_falls_back_to_general():
    router = build_router(BrokenLLM())
    assert await router(MESSAGES) == "general"


def test_intent_node_returns_mock_reply():
    node = build_intent_node("pharmacy")
    assert node({"messages": MESSAGES, "intent": "pharmacy", "reply": ""}) == {
        "reply": MOCK_REPLIES["pharmacy"],
        "tool_calls": [],
    }
