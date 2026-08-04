"""导诊（triage）意图节点编排（ticket 11）。

多轮症状采集 → 紧急症状拦截 → 知识图谱查询 → 医生推荐 → 回复生成。
节点函数被 intents.py 的 build_intent_node("triage") 调用，替换 09 骨架的 mock 回复。
"""

import json
import logging
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel

from app.emotion import Emotion, apply_emotion_care, detect_emotion, inject_emotion
from app.tool_client import call_java_tool

logger = logging.getLogger(__name__)

# 紧急症状关键词——命中任一个即建议立即就医
_EMERGENCY_SYMPTOMS = {"胸痛", "大出血", "呼吸困难", "无法呼吸", "休克", "昏迷"}

# 需采集的症状维度
_SYMPTOM_DIMENSIONS = ["部位", "持续时间", "严重程度", "伴随症状", "体温"]

# 导诊系统 prompt
_TRIAGE_SYSTEM_PROMPT = """你是智愈医疗的智能导诊助手。你的任务是帮助患者分析症状并推荐合适的科室和医生。

请遵循以下流程：
1. 收集症状信息：部位、持续时间、严重程度、伴随症状、体温
2. 判断是否紧急：如果患者提到胸痛、大出血、呼吸困难、休克或昏迷，立即建议就医
3. 信息足够时，回复 JSON 格式的决策结果

回复格式：
- 信息不足需追问：自然语言追问，语气亲切专业
- 信息已足够或紧急：输出 JSON 格式
  ```json
  {{"action": "recommend", "symptoms": ["症状1", "症状2"], "keywords": ["关键词1", "关键词2"]}}
  ```
  其中 keywords 为用于查询知识图谱的关键词列表（症状名+疑似疾病名）

注意：
- 一次只追问 1-2 个问题，不要让患者感觉被审问
- 紧急情况直接建议就医，不要追问
- 所有推荐必须标注"AI 建议仅供参考"
"""


def _extract_json_from_llm(content: str) -> dict[str, Any] | None:
    """从 LLM 回复中提取 JSON 块（兼容 ```json 包裹和纯 JSON）。"""
    # 尝试找 ```json 块
    if "```json" in content:
        start = content.index("```json") + 7
        end = content.index("```", start) if "```" in content[start:] else len(content)
        json_str = content[start:end].strip()
    else:
        # 尝试全文解析
        json_str = content.strip()

    try:
        return json.loads(json_str)
    except json.JSONDecodeError:
        return None


def _detect_emergency(messages: list[dict[str, str]]) -> bool:
    """检测消息中是否包含紧急症状。

    支持子串匹配和字符级匹配（"胸口痛"匹配"我胸口很痛"）。
    """
    for msg in messages:
        content = msg.get("content", "")
        for symptom in _EMERGENCY_SYMPTOMS:
            # 直接子串匹配
            if symptom in content:
                return True
            # 字符级匹配：检查症状的每个汉字是否都在消息中
            chars = [c for c in symptom if '一' <= c <= '鿿']
            if len(chars) >= 2 and all(c in content for c in chars):
                return True
    return False


def _format_doctor_recommendation(doctors_data: dict[str, Any]) -> str:
    """格式化医生推荐文本。"""
    doctors = doctors_data.get("doctors", [])
    if not doctors:
        return ""

    lines = ["**推荐医生：**"]
    for i, doc in enumerate(doctors[:3], 1):
        name = doc.get("name", "未知")
        title = doc.get("title", "")
        specialty = doc.get("specialty", "")
        good_rate = doc.get("goodRate")
        intro = doc.get("intro", "")
        slots = doc.get("availableSlots", [])

        rate_str = f"好评率 {float(good_rate) * 100:.0f}%" if good_rate is not None else ""
        slot_count = len(slots)
        slot_str = f"（{slot_count} 个可约时段）" if slot_count > 0 else "（暂无可约号源）"

        parts = [f"{i}. **{name}**"]
        if title:
            parts.append(title)
        if specialty:
            parts.append(f"擅长 {specialty}")
        if rate_str:
            parts.append(rate_str)
        parts.append(slot_str)
        if intro:
            parts.append(f"— {intro}")

        lines.append("  ".join(parts))
    return "\n".join(lines)


def _format_kg_recommendation(kg_data: dict[str, Any]) -> str:
    """格式化知识图谱推荐文本。"""
    results = kg_data.get("results", [])
    if not results:
        return ""

    # 去重科室
    seen_depts = set()
    dept_lines = []
    for r in results:
        dept = r.get("department")
        if dept and dept not in seen_depts:
            seen_depts.add(dept)
            desc = r.get("departmentDesc", "")
            reason = r.get("reason", "")
            line = f"- **{dept}**"
            if desc:
                line += f"：{desc}"
            if reason:
                line += f"\n  理由：{reason}"
            dept_lines.append(line)

    if not dept_lines:
        return ""

    header = "**根据您的症状，建议就诊以下科室：**"
    return header + "\n" + "\n".join(dept_lines)


def build_triage_node(llm: BaseChatModel):
    """构建导诊意图节点。

    Args:
        llm: LangChain chat model 实例（用于症状分析对话）

    Returns:
        node 函数：输入 state，输出 {"reply": str, "tool_calls": list[dict]}
    """

    async def node(state: dict[str, Any]) -> dict[str, Any]:
        messages = state.get("messages", [])
        if not messages:
            return {"reply": "您好，我是智愈健康助手，请描述您的症状，我可以帮您分析并推荐合适的科室和医生。"}

        # 0. 情感识别（旁路能力，ticket 15）：影响语气与紧急判定
        emotion = await detect_emotion(messages, llm=llm)

        # 1. 检查紧急症状（疼痛情绪协同：剧烈疼痛同样触发紧急建议 + 快速导诊）
        if _detect_emergency(messages) or emotion == Emotion.PAIN:
            return {
                "reply": apply_emotion_care(
                    (
                        "⚠️ **检测到您描述的可能是紧急症状，请立即就医！**\n\n"
                        "您提到的症状需要紧急医疗处理，请立即前往最近医院的急诊科，"
                        "或拨打 120 急救电话。\n\n"
                        "本对话为 AI 辅助，不能替代专业医疗判断。"
                    ),
                    emotion,
                ),
            }

        # 2. 用 LLM 分析症状信息完整性（system prompt 注入情绪语气指令）
        llm_messages = [
            {"role": "system", "content": inject_emotion(_TRIAGE_SYSTEM_PROMPT, emotion)},
            *messages,
        ]

        try:
            res = await llm.ainvoke(llm_messages)
            reply_content = res.content if isinstance(res.content, str) else str(res.content)
        except Exception as e:
            logger.error("导诊 LLM 调用失败", exc_info=e)
            return {"reply": "抱歉，我暂时无法分析您的症状，请稍后重试或直接前往医院就诊。"}

        # 3. 尝试解析 JSON 决策
        decision = _extract_json_from_llm(reply_content)
        if decision and decision.get("action") == "recommend":
            keywords = decision.get("keywords", [])
            tool_calls = []

            # 3a. 查询知识图谱
            kg_results = {}
            if keywords:
                tool_calls.append({"tool": "query_knowledge_graph", "label": "正在查询知识图谱，匹配症状与科室..."})
                try:
                    kg_results = await call_java_tool("query_knowledge_graph", {"keyword": " ".join(keywords)})
                except RuntimeError as e:
                    logger.warning("知识图谱查询失败: %s", e)

            # 3b. 查询医生推荐（取知识图谱推荐的首个科室）
            doctors_results = {}
            kg_results_list = kg_results.get("results", [])
            if kg_results_list:
                # 取第一个科室的 department_id
                dept_name = kg_results_list[0].get("department", "")
                # 先用科室名查科室 ID
                try:
                    dept_data = await call_java_tool("query_departments", {"name": dept_name})
                    dept_page = dept_data.get("departments", {})
                    dept_records = dept_page.get("records", [])
                    if dept_records:
                        dept_id = dept_records[0].get("id")
                        tool_calls.append({"tool": "query_doctors", "label": f"正在查询{dept_name}的医生推荐..."})
                        try:
                            doctors_results = await call_java_tool("query_doctors", {"department_id": dept_id})
                        except RuntimeError as e:
                            logger.warning("医生查询失败: %s", e)
                except RuntimeError as e:
                    logger.warning("科室查询失败: %s", e)

            # 3c. 生成推荐回复
            parts = ["根据您的症状描述，以下是为您提供的导诊建议：\n"]

            # 知识图谱建议
            kg_text = _format_kg_recommendation(kg_results)
            if kg_text:
                parts.append(kg_text)
                parts.append("")

            # 医生推荐
            doc_text = _format_doctor_recommendation(doctors_results)
            if doc_text:
                parts.append(doc_text)
                parts.append("")

            # 补充说明
            parts.append("---")
            parts.append("💡 **温馨提示：**")
            parts.append("- 以上推荐基于您描述的症状信息")
            parts.append("- 实际诊断请以医生面诊为准")
            parts.append("- 您可以在挂号后与医生详细沟通病情")
            parts.append("")
            parts.append("🤖 *AI 建议仅供参考，不能替代专业医疗诊断。如症状加重，请及时就医。*")

            return {
                "reply": apply_emotion_care("\n".join(parts), emotion),
                "tool_calls": tool_calls,
            }

        # 4. 信息不足，返回 LLM 追问
        return {"reply": apply_emotion_care(reply_content, emotion)}

    return node