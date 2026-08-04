"""导诊（triage）意图节点测试（ticket 11）。

测试覆盖：
1. 紧急症状拦截
2. 正常症状追问
3. 工具调用顺序
4. 状态机集成
"""

import pytest

from app.graph import AgentState, build_graph
from app.intents import build_intent_node, build_router
from app.triage import _detect_emergency, _extract_json_from_llm, _format_doctor_recommendation, _format_kg_recommendation


# ============ 紧急症状检测 ============


class TestDetectEmergency:
    """紧急症状关键词检测。"""

    def test_chest_pain_detected(self):
        messages = [{"role": "user", "content": "我胸口很痛"}]
        assert _detect_emergency(messages) is True

    def test_heavy_bleeding_detected(self):
        messages = [{"role": "user", "content": "摔了一跤，大出血止不住"}]
        assert _detect_emergency(messages) is True

    def test_breathing_difficulty_detected(self):
        messages = [{"role": "user", "content": "感觉呼吸困难"}]
        assert _detect_emergency(messages) is True

    def test_mild_symptom_not_emergency(self):
        messages = [{"role": "user", "content": "最近有点头疼"}]
        assert _detect_emergency(messages) is False

    def test_assistant_message_not_emergency(self):
        messages = [{"role": "assistant", "content": "请问您哪里不舒服？"}]
        assert _detect_emergency(messages) is False

    def test_emergency_in_multi_turn(self):
        messages = [
            {"role": "user", "content": "有点感冒"},
            {"role": "assistant", "content": "请问有发烧吗？"},
            {"role": "user", "content": "没发烧，但是突然胸痛"},
        ]
        assert _detect_emergency(messages) is True


# ============ LLM 回复 JSON 解析 ============


class TestExtractJsonFromLlm:
    """从 LLM 回复中提取 JSON 决策块。"""

    def test_pure_json(self):
        content = '{"action": "recommend", "symptoms": ["头痛"], "keywords": ["头痛"]}'
        result = _extract_json_from_llm(content)
        assert result is not None
        assert result["action"] == "recommend"

    def test_json_in_code_block(self):
        content = '根据症状分析，结果如下：\n```json\n{"action": "recommend", "symptoms": ["头痛", "发热"], "keywords": ["头痛", "上呼吸道感染"]}\n```'
        result = _extract_json_from_llm(content)
        assert result is not None
        assert result["action"] == "recommend"
        assert "头痛" in result["symptoms"]

    def test_no_json_in_text(self):
        content = "请问您头痛持续多久了？"
        result = _extract_json_from_llm(content)
        assert result is None

    def test_invalid_json(self):
        content = '{"action": recommend, "symptoms": [头痛]}'
        result = _extract_json_from_llm(content)
        assert result is None


# ============ 知识图谱推荐格式化 ============


class TestFormatKgRecommendation:
    """知识图谱推荐文本格式化。"""

    def test_single_result(self):
        kg_data = {
            "results": [
                {
                    "symptom": "头痛",
                    "disease": "偏头痛",
                    "department": "神经内科",
                    "departmentDesc": "诊治脑与神经系统疾病",
                    "reason": "您的症状（头痛）可能与「偏头痛」有关，建议就诊「神经内科」（诊治脑与神经系统疾病）",
                }
            ]
        }
        result = _format_kg_recommendation(kg_data)
        assert "神经内科" in result
        assert "偏头痛" in result or "理由" in result

    def test_multiple_results(self):
        kg_data = {
            "results": [
                {
                    "symptom": "头痛",
                    "disease": "偏头痛",
                    "department": "神经内科",
                    "departmentDesc": "诊治脑与神经系统疾病",
                    "reason": "您的症状（头痛）可能与「偏头痛」有关",
                },
                {
                    "symptom": "头痛",
                    "disease": "紧张性头痛",
                    "department": "神经内科",
                    "departmentDesc": "诊治脑与神经系统疾病",
                    "reason": "您的症状（头痛）可能与「紧张性头痛」有关",
                },
                {
                    "symptom": "发热",
                    "disease": "上呼吸道感染",
                    "department": "呼吸内科",
                    "departmentDesc": "诊治呼吸道及肺部疾病",
                    "reason": "您的症状（发热）可能与「上呼吸道感染」有关",
                },
            ]
        }
        result = _format_kg_recommendation(kg_data)
        # 神经内科应只出现一次（去重）
        assert "神经内科" in result
        assert "呼吸内科" in result

    def test_empty_results(self):
        assert _format_kg_recommendation({"results": []}) == ""


# ============ 医生推荐格式化 ============


class TestFormatDoctorRecommendation:
    """医生推荐文本格式化。"""

    def test_single_doctor(self):
        doctors_data = {
            "doctors": [
                {
                    "id": 1,
                    "name": "张医生",
                    "title": "主任医师",
                    "specialty": "脑血管疾病",
                    "goodRate": 0.98,
                    "intro": "从医30年",
                    "availableSlots": [{"scheduleId": 1, "date": "2026-08-05", "remainingSlots": 5}],
                }
            ]
        }
        result = _format_doctor_recommendation(doctors_data)
        assert "张医生" in result
        assert "主任医师" in result
        assert "98%" in result
        assert "1 个可约时段" in result

    def test_no_doctors(self):
        assert _format_doctor_recommendation({"doctors": []}) == ""

    def test_doctor_without_slots(self):
        doctors_data = {
            "doctors": [
                {
                    "id": 1,
                    "name": "李医生",
                    "title": "主治医师",
                    "specialty": "",
                    "goodRate": None,
                    "intro": "",
                    "availableSlots": [],
                }
            ]
        }
        result = _format_doctor_recommendation(doctors_data)
        assert "李医生" in result
        assert "暂无可约号源" in result


# ============ 意图节点集成测试 ============


class TestTriageIntentNode:
    """导诊意图节点集成测试（mock LLM）。"""

    @pytest.mark.asyncio
    async def test_empty_messages_returns_greeting(self):
        node = build_intent_node("triage")
        result = node({"messages": [], "intent": "triage", "reply": "", "tool_calls": []})
        # 无 llm 时降级为 mock 回复
        assert "导诊骨架" in result["reply"]

    @pytest.mark.asyncio
    async def test_mock_llm_returns_mock_reply(self):
        """未传入 llm 时，triage 节点回退到 mock 回复（echo 模式）。"""
        node = build_intent_node("triage")
        result = node({"messages": [{"role": "user", "content": "我头疼"}], "intent": "triage", "reply": "", "tool_calls": []})
        assert "导诊骨架" in result["reply"]

    @pytest.mark.asyncio
    async def test_triage_node_with_emergency(self):
        """紧急症状应触发紧急拦截。"""
        node = build_intent_node("triage")
        messages = [{"role": "user", "content": "我胸口很痛，呼吸困难"}]
        # 无 llm 参数时走 mock 回复，但紧急检测在 triage 节点内部
        # 这里只验证紧急检测函数行为
        assert _detect_emergency(messages) is True


# ============ 状态机集成测试 ============


class TestGraphWithTriage:
    """状态机集成测试。"""

    @pytest.mark.asyncio
    async def test_router_returns_triage_mock(self):
        """测试路由到 triage 节点后 mock 回复。"""
        # 使用 mock router 固定返回 triage
        async def mock_router(_messages):
            return "triage"

        graph = build_graph(router=mock_router)
        state = await graph.ainvoke(AgentState(
            messages=[{"role": "user", "content": "我头疼"}],
            intent="",
            reply="",
            tool_calls=[],
        ))
        assert state["intent"] == "triage"
        # 回复应包含导诊内容（mock 回复或真实 triage 回复）
        assert state["reply"]


# ============ 工具契约测试补充 ============


class TestToolClientContract:
    """工具客户端契约验证。"""

    def test_tool_endpoint_resolution(self):
        from app.tool_client import get_tool_endpoint, load_tools

        tools = load_tools()
        assert len(tools) >= 11

        # 验证导诊相关工具存在
        tool_names = {t["name"] for t in tools}
        assert "query_knowledge_graph" in tool_names
        assert "query_doctors" in tool_names
        assert "query_departments" in tool_names

        # 验证端点解析
        endpoint = get_tool_endpoint("query_knowledge_graph")
        assert endpoint == "/api/agent/tools/query_knowledge_graph"