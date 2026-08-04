"""挂号（registration）意图节点编排（ticket 12）。

纯确定性编排（无 LLM 参与）：
1. 用户表达挂号意图 → 查询排班（按科室/医生/日期）
2. 展示排班选项（文本 + 工具调用轨迹）
3. 用户选择时段 → 调用 create_registration_draft 工具
4. 生成确认卡片（card 事件），前端渲染，用户点击确认后直调 Java
"""

import logging
import re
from typing import Any

from app.tool_client import call_java_tool
from app.sse import card_event

logger = logging.getLogger(__name__)

# 挂号引导提示
_REGISTRATION_GREETING = (
    "好的，我来帮您挂号。请问您想挂哪个科室的号？"
    "\n\n或者您可以直接告诉我：\n"
    "- 科室名称（如：神经内科）\n"
    "- 医生姓名（如：张医生）\n"
    "- 日期（如：明天）\n"
    "也可以直接说「帮我挂之前的号」来查看历史记录。"
)

# 排班选择提示
_SCHEDULE_PROMPT = "请从以上排班中选择一个时段（回复编号），或告诉我其他需求。"


def _format_schedule_options(schedules: list[dict[str, Any]]) -> str:
    """格式化排班列表为文本选项。"""
    if not schedules:
        return "当前没有可用的排班号源。请稍后再试或联系客服。"

    lines = ["**以下是可预约的时段：**\n"]
    for i, s in enumerate(schedules, 1):
        dept = s.get("departmentName", "未知科室")
        doctor = s.get("doctorName", "未知医生")
        date = s.get("scheduleDate", "")
        period = s.get("timePeriod", "")
        time_range = s.get("timeRange", "")
        slots = s.get("remainingSlots", 0)

        period_label = {"MORNING": "上午", "AFTERNOON": "下午", "EVENING": "晚上"}.get(period, period)
        lines.append(f"{i}. **{doctor}** | {dept}")
        lines.append(f"   {date} {period_label}（{time_range}）— 余 {slots} 号")

    lines.append("")
    lines.append(_SCHEDULE_PROMPT)
    return "\n".join(lines)


def _extract_schedule_choice(user_message: str, schedules: list[dict]) -> dict | None:
    """从用户消息中提取排班选择。

    支持：
    - "选第1个" / "第一个" / "1" 等编号选择（含中文数字）
    """
    text = user_message.strip()

    # 中文数字映射
    _CN_NUM = {"零": 0, "一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
               "六": 6, "七": 7, "八": 8, "九": 9}

    # 先尝试匹配中文数字："第X个" / "X个" / 单独中文数字
    cn_match = re.search(r"[第]?(?:[零一二三四五六七八九十]+?)([个位])?", text)
    if cn_match:
        cn_num_str = cn_match.group(0).replace("第", "").replace("个", "").replace("位", "")
        if cn_num_str in _CN_NUM:
            idx = _CN_NUM[cn_num_str]
            if 1 <= idx <= len(schedules):
                return schedules[idx - 1]

    # 再尝试匹配阿拉伯数字
    match = re.search(r"[第]?(\d+)[个位]?", text)
    if match:
        idx = int(match.group(1))
        if 1 <= idx <= len(schedules):
            return schedules[idx - 1]

    return None


def build_registration_node():
    """构建挂号意图节点。

    Returns:
        node 函数：输入 state，输出 {"reply": str, "tool_calls": list[dict], "card": str|None}
    """
    # 状态：当前轮次中的排班列表（在多次调用间保持）
    _current_schedules: list[dict] = []

    async def node(state: dict[str, Any]) -> dict[str, Any]:
        nonlocal _current_schedules
        messages = state.get("messages", [])
        if not messages:
            return {"reply": _REGISTRATION_GREETING}

        # 获取用户最新消息
        last_user_msg = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                last_user_msg = msg.get("content", "")
                break

        if not last_user_msg:
            return {"reply": _REGISTRATION_GREETING}

        # 判断用户是否在排班列表中做了选择
        if _current_schedules:
            selected = _extract_schedule_choice(last_user_msg, _current_schedules)
            if selected:
                # 用户选择了排班 → 创建挂号草稿
                schedule_id = selected.get("scheduleId")
                tool_calls = [{"tool": "create_registration_draft", "label": "正在创建挂号草稿..."}]

                try:
                    draft_result = await call_java_tool("create_registration_draft", {
                        "schedule_id": schedule_id,
                    })
                except RuntimeError as e:
                    logger.warning("创建草稿失败: %s", e)
                    _current_schedules = []  # 重置状态，让用户重新开始
                    return {
                        "reply": (
                            f"⚠️ 创建挂号草稿失败：{e}\n\n"
                            "请稍后重试，或重新选择排班时段。"
                        ),
                        "tool_calls": tool_calls,
                    }

                # 草稿创建成功 → 生成确认卡片
                _current_schedules = []  # 重置状态

                # 构建 card 事件
                card = card_event(
                    card_type="registration_confirm",
                    title="确认挂号信息",
                    action="/api/c/registrations/confirm",
                    payload={
                        "scheduleId": draft_result.get("scheduleId"),
                        "confirmToken": draft_result.get("confirmToken"),
                        "familyMemberId": draft_result.get("familyMemberId"),
                        "doctorName": draft_result.get("doctorName", ""),
                        "departmentName": draft_result.get("departmentName", ""),
                        "scheduleDate": str(draft_result.get("scheduleDate", "")),
                        "timePeriod": draft_result.get("timePeriod", ""),
                        "timeRange": draft_result.get("timeRange", ""),
                        "visitorName": draft_result.get("visitorName", "本人"),
                    },
                )

                period_label = {"MORNING": "上午", "AFTERNOON": "下午", "EVENING": "晚上"}.get(
                    draft_result.get("timePeriod", ""), draft_result.get("timePeriod", ""))

                reply = (
                    f"已为您创建挂号草稿：\n"
                    f"👤 **{draft_result.get('visitorName', '本人')}**\n"
                    f"🏥 {draft_result.get('departmentName', '')} | "
                    f"👨‍⚕️ {draft_result.get('doctorName', '')}\n"
                    f"📅 {draft_result.get('scheduleDate', '')} {period_label}\n\n"
                    "请点击下方卡片确认挂号。"
                )

                return {
                    "reply": reply,
                    "tool_calls": tool_calls,
                    "card": card,
                }

            # 用户输入不是有效选择，重新提示
            return {
                "reply": _SCHEDULE_PROMPT,
            }

        # 未查询排班 → 查询排班
        tool_calls = [{"tool": "query_schedule", "label": "正在查询可预约排班..."}]

        try:
            schedule_result = await call_java_tool("query_schedule", {})
        except RuntimeError as e:
            logger.warning("排班查询失败: %s", e)
            return {
                "reply": "⚠️ 排班查询失败，请稍后重试或联系客服。",
                "tool_calls": tool_calls,
            }

        schedules = schedule_result.get("schedules", [])
        _current_schedules = schedules

        reply = _format_schedule_options(schedules)
        return {
            "reply": reply,
            "tool_calls": tool_calls,
        }

    return node