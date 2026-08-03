"""SSE 输出格式测试（09 ticket，ADR-0014）。

通过 httpx ASGITransport 直打 FastAPI 应用，验证：
- echo 模式：事件序列 delta... → done，data 为 JSON，Content-Type 为 text/event-stream
- secret 校验：缺头/错误密钥 → 401
- 真实模式（mock graph）：delta → done 事件流
"""

import pytest
from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import app

BASE = "http://test"
SECRET = "test-agent-secret"


async def test_echo_mode_returns_delta_then_done():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        # 消息控制在单块内（_CHUNK_SIZE=4），便于断言回显内容
        resp = await client.post(
            "/agent/chat",
            json={"messages": [{"role": "user", "content": "你好"}]},
            headers={"X-Agent-Secret": SECRET},
        )
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("text/event-stream")
    text = resp.text
    assert 'event: delta' in text
    assert 'event: done' in text
    # 事件顺序：delta 在前、done 收尾
    assert text.index("event: delta") < text.index("event: done")
    # data 为 JSON 且携带回显文本（ensure_ascii=False，中文原样）
    assert '"text": "你好"' in text


async def test_echo_mode_only_echoes_last_user_message():
    """echo 只回显最后一条 user 消息：末条是 assistant 时回显更早的 user 消息。"""
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        resp = await client.post(
            "/agent/chat",
            json={"messages": [
                {"role": "user", "content": "用户问"},
                {"role": "assistant", "content": "助手回答"},
                {"role": "user", "content": "再问"},
                {"role": "assistant", "content": "助手再答"},
            ]},
            headers={"X-Agent-Secret": SECRET},
        )
    assert resp.status_code == 200
    text = resp.text
    # 消息长度控制 ≤ _CHUNK_SIZE=4，整条落在单块内便于断言
    assert '"再问"' in text
    assert '"助手再答"' not in text


async def test_missing_secret_returns_401():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        resp = await client.post("/agent/chat", json={"messages": [{"role": "user", "content": "hi"}]})
    assert resp.status_code == 401


async def test_wrong_secret_returns_401():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        resp = await client.post(
            "/agent/chat",
            json={"messages": [{"role": "user", "content": "hi"}]},
            headers={"X-Agent-Secret": "wrong-secret"},
        )
    assert resp.status_code == 401


async def test_health_is_public():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        resp = await client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "UP"}


class FakeGraph:
    """模拟编译后的 LangGraph：直接返回给定回复。"""

    def __init__(self, reply: str):
        self._reply = reply

    async def ainvoke(self, state):
        return {**state, "reply": self._reply}


async def test_agent_mode_streams_delta_then_done(monkeypatch):
    """真实模式（mock graph + 非 echo）：delta 事件流 + done 收尾。"""
    monkeypatch.setattr("app.main.get_settings", lambda: Settings(agent_echo_mode=False))
    monkeypatch.setattr("app.main.get_graph", lambda: FakeGraph("测试回复"))

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        resp = await client.post(
            "/agent/chat",
            json={"messages": [{"role": "user", "content": "我头疼"}]},
            headers={"X-Agent-Secret": SECRET},
        )
    assert resp.status_code == 200
    text = resp.text
    assert 'event: delta' in text
    assert 'event: done' in text
    assert '"测试回复"' in text


async def test_agent_mode_error_falls_back_to_error_event(monkeypatch):
    """graph 异常时输出 error 事件收尾（不抛给客户端）。"""
    monkeypatch.setattr("app.main.get_settings", lambda: Settings(agent_echo_mode=False))

    class BrokenGraph:
        async def ainvoke(self, _state):
            raise RuntimeError("boom")

    monkeypatch.setattr("app.main.get_graph", lambda: BrokenGraph())

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url=BASE) as client:
        resp = await client.post(
            "/agent/chat",
            json={"messages": [{"role": "user", "content": "我头疼"}]},
            headers={"X-Agent-Secret": SECRET},
        )
    assert resp.status_code == 200
    text = resp.text
    assert 'event: error' in text
    assert '"服务暂时不可用，请稍后重试"' in text
