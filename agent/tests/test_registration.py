"""挂号（registration）意图节点测试（ticket 12）。

测试覆盖：
1. 排班格式化
2. 排班选择提取
3. 意图节点基础行为（空消息、查询工具调用）
"""

import pytest

from app.intents import build_intent_node
from app.registration import _format_schedule_options, _extract_schedule_choice


# ============ 排班格式化 ============


class TestFormatScheduleOptions:
    """排班列表格式化。"""

    def test_single_schedule(self):
        schedules = [
            {
                "scheduleId": 1,
                "doctorId": 1,
                "doctorName": "张医生",
                "departmentName": "神经内科",
                "scheduleDate": "2026-08-05",
                "timePeriod": "MORNING",
                "timeRange": "08:00-12:00",
                "remainingSlots": 5,
                "status": "PUBLISHED",
            }
        ]
        result = _format_schedule_options(schedules)
        assert "张医生" in result
        assert "神经内科" in result
        assert "2026-08-05" in result
        assert "5" in result
        assert "1." in result

    def test_multiple_schedules(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生", "departmentName": "神经内科",
             "scheduleDate": "2026-08-05", "timePeriod": "MORNING", "timeRange": "08:00-12:00",
             "remainingSlots": 5, "status": "PUBLISHED"},
            {"scheduleId": 2, "doctorName": "李医生", "departmentName": "神经内科",
             "scheduleDate": "2026-08-05", "timePeriod": "AFTERNOON", "timeRange": "14:00-17:00",
             "remainingSlots": 3, "status": "PUBLISHED"},
        ]
        result = _format_schedule_options(schedules)
        assert "1." in result
        assert "2." in result
        assert "上午" in result
        assert "下午" in result

    def test_empty_schedules(self):
        result = _format_schedule_options([])
        assert "没有可用的排班" in result

    def test_period_label_mapping(self):
        """验证时段标签映射。"""
        schedules = [
            {"scheduleId": 1, "doctorName": "王医生", "departmentName": "呼吸内科",
             "scheduleDate": "2026-08-05", "timePeriod": "EVENING", "timeRange": "18:00-21:00",
             "remainingSlots": 2, "status": "PUBLISHED"},
        ]
        result = _format_schedule_options(schedules)
        assert "晚上" in result


# ============ 排班选择提取 ============


class TestExtractScheduleChoice:
    """从用户消息中提取排班选择。"""

    def test_number_choice(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生"},
            {"scheduleId": 2, "doctorName": "李医生"},
        ]
        result = _extract_schedule_choice("1", schedules)
        assert result is not None
        assert result["scheduleId"] == 1

    def test_chinese_number_choice(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生"},
            {"scheduleId": 2, "doctorName": "李医生"},
        ]
        result = _extract_schedule_choice("选第一个", schedules)
        assert result is not None
        assert result["scheduleId"] == 1

    def test_second_choice(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生"},
            {"scheduleId": 2, "doctorName": "李医生"},
        ]
        result = _extract_schedule_choice("第2个", schedules)
        assert result is not None
        assert result["scheduleId"] == 2

    def test_invalid_choice(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生"},
        ]
        result = _extract_schedule_choice("abc", schedules)
        assert result is None

    def test_out_of_range(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生"},
        ]
        result = _extract_schedule_choice("5", schedules)
        assert result is None

    def test_negative_number(self):
        schedules = [
            {"scheduleId": 1, "doctorName": "张医生"},
        ]
        result = _extract_schedule_choice("0", schedules)
        assert result is None


# ============ 意图节点基础测试 ============


class TestRegistrationIntentNode:
    """挂号意图节点基础行为测试。"""

    @pytest.mark.asyncio
    async def test_empty_messages_returns_greeting(self):
        node = build_intent_node("registration")
        result = await node({"messages": [], "intent": "registration", "reply": "", "tool_calls": []})
        assert "挂号" in result["reply"]
        assert "科室" in result["reply"]

    @pytest.mark.asyncio
    async def test_node_returns_dict_with_expected_keys(self):
        node = build_intent_node("registration")
        # 空消息 → 只返回 reply（无 tool_calls）
        result = await node({"messages": [], "intent": "registration", "reply": "", "tool_calls": []})
        assert "reply" in result
        # 有消息时才会包含 tool_calls
        result2 = await node({"messages": [{"role": "user", "content": "我要挂号"}], "intent": "registration", "reply": "", "tool_calls": []})
        assert "reply" in result2
        assert "tool_calls" in result2
