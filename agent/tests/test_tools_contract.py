"""工具契约测试（09 ticket）。

钉死 tools.json 的结构约束：12 个工具、name 唯一、字段齐全、
动作类工具无 confirm（ADR-0015）、必填参数合理。
"""

import json
from pathlib import Path

TOOLS_JSON = Path(__file__).resolve().parents[1] / "tools" / "tools.json"

EXPECTED_TOOL_COUNT = 12
# ADR-0015：confirm 类工具不入 Agent 工具集
FORBIDDEN_TOOLS = {"confirm_registration", "confirm_order"}


def load_tools() -> list[dict]:
    data = json.loads(TOOLS_JSON.read_text(encoding="utf-8"))
    assert "tools" in data, "tools.json 缺少 tools 数组"
    return data["tools"]


def test_tool_count_is_12():
    assert len(load_tools()) == EXPECTED_TOOL_COUNT


def test_tool_names_unique_and_snake_case():
    tools = load_tools()
    names = [t["name"] for t in tools]
    assert len(names) == len(set(names)), "工具名重复"
    for name in names:
        assert name.islower() and "_" in name, f"工具名必须 snake_case: {name}"


def test_required_fields_present():
    for t in load_tools():
        assert t.get("name"), "缺少 name"
        assert t.get("description"), f"{t['name']} 缺少 description"
        assert t.get("type") in ("query", "action"), f"{t['name']} type 非法"
        assert t["java_endpoint"].startswith("/api/agent/tools/"), f"{t['name']} java_endpoint 非法"
        params = t["parameters"]
        assert params["type"] == "object", f"{t['name']} parameters 必须是 object"
        assert isinstance(params["properties"], dict), f"{t['name']} 缺少 properties"
        assert isinstance(params["required"], list), f"{t['name']} 缺少 required"


def test_no_confirm_tools():
    """ADR-0015：Agent 只建草稿，确认类工具由前端凭卡片 action 直调 Java。"""
    names = {t["name"] for t in load_tools()}
    assert not names & FORBIDDEN_TOOLS, f"工具集不得包含: {names & FORBIDDEN_TOOLS}"


def test_action_tools_have_required_params():
    for t in load_tools():
        if t["type"] == "action":
            assert t["parameters"]["required"], f"动作类工具 {t['name']} 必须有必填参数"


def test_java_endpoint_matches_name():
    """泛化路由约定：java_endpoint 尾部 = 工具名。"""
    for t in load_tools():
        assert t["java_endpoint"].endswith("/" + t["name"]), f"{t['name']} 端点与工具名不一致"
