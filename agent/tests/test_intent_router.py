"""意图路由测试（09 ticket）。

- 注入 fake router：验证 StateGraph 条件边把每个意图路由到对应节点并返回 mock 回复
- 真实 router 构建：验证结构化输出解析、未知意图兜底 general、LLM 异常兜底 general
"""

import pytest
from types import SimpleNamespace

from app.graph import AgentState, build_graph
from app.intents import INTENTS, IntentResult, MOCK_REPLIES, build_intent_node, build_router

MESSAGES = [{"role": "user", "content": "我头疼"}]


def make_fake_router(intent: str):
    async def route(_messages):
        return intent

    return route


@pytest.mark.parametrize("intent", INTENTS)
async def test_graph_routes_to_correct_intent_node(intent):
    """每个意图都有一条条件边 → 对应节点 → mock 回复。"""
    graph = build_graph(router=make_fake_router(intent))
    state = await graph.ainvoke(AgentState(messages=MESSAGES, intent="", reply=""))
    assert state["intent"] == intent
    assert state["reply"] == MOCK_REPLIES[intent]


class FakeLLM:
    """模拟 ChatOpenAI：with_structured_output 返回自身，ainvoke 由子类决定。"""

    def with_structured_output(self, schema):
        return self


class ReturningLLM(FakeLLM):
    def __init__(self, result):
        self._result = result

    async def ainvoke(self, _messages):
        return self._result


class BrokenLLM(FakeLLM):
    async def ainvoke(self, _messages):
        raise RuntimeError("network down")


async def test_router_returns_parsed_intent():
    router = build_router(ReturningLLM(IntentResult(intent="triage")))
    assert await router(MESSAGES) == "triage"


async def test_router_unknown_intent_falls_back_to_general():
    """LLM 返回未定义意图名时兜底 general（绕过 pydantic 构造，模拟结构化输出返回任意值）。"""
    router = build_router(ReturningLLM(SimpleNamespace(intent="hacking")))
    assert await router(MESSAGES) == "general"


async def test_router_llm_failure_falls_back_to_general():
    router = build_router(BrokenLLM())
    assert await router(MESSAGES) == "general"


def test_intent_node_returns_mock_reply():
    node = build_intent_node("pharmacy")
    assert node({"messages": MESSAGES, "intent": "pharmacy", "reply": ""}) == {
        "reply": MOCK_REPLIES["pharmacy"]
    }
