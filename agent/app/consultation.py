"""预问诊与处方解读（consultation）意图节点编排（ticket 13）。

处理三种场景：
1. 预问诊流程：引导患者补充主诉、现病史、既往史、过敏史 → 生成摘要卡片
2. 处方解读：查询处方详情 → 通俗语言解释 → 过敏风险检测
3. 过敏阻断：检测到过敏风险 → 高亮警告 → 阻断购药流程

节点函数被 intents.py 的 build_intent_node("consultation") 调用。
"""

import json
import logging
import re
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel

from app.emotion import Emotion, apply_emotion_care, detect_emotion, inject_emotion
from app.tool_client import call_java_tool
from app.sse import card_event

logger = logging.getLogger(__name__)

# 预问诊系统 prompt
_PRE_CONSULT_PROMPT = """你是一个医疗预问诊助手。你需要引导患者补充以下信息，每次只问 1-2 个问题：

1. 主诉（chief complaint）：主要症状、持续时间
2. 现病史（present history）：发病经过、诊疗经过
3. 既往史（past history）：基础疾病、手术史
4. 过敏史（allergy history）：药物/食物过敏情况

当信息收集完整时，输出 JSON 格式的总结：
```json
{{"action": "summary", "chief_complaint": "...", "present_history": "...", "past_history": "...", "allergy_history": "..."}}
```

注意：
- 一次只问 1-2 个问题，不要一次性问完
- 语气亲切专业
- 信息收集完整后生成 JSON 总结
"""

# 处方解读系统 prompt
_PRESCRIPTION_INTERPRET_PROMPT = """你是一个用药助手，负责用通俗易懂的语言解释处方内容。

处方信息包含以下字段：
- drug_name: 药品名称
- usage_method: 用法（如口服、外用）
- dosage: 剂量（如 0.3g）
- frequency: 频率（如每日2次）
- remark: 备注（如饭后）

请对每种药物进行解释：
1. 作用：用通俗语言说明该药是治什么的
2. 吃法：解释用法用量
3. 注意事项：基于 remark 和其他信息给出提醒

在回答末尾添加：
---
*AI 解读仅供参考，请遵医嘱服用。*
"""


_KNOWN_DRUGS = [
    "阿莫西林", "头孢", "头孢克肟", "头孢拉定", "青霉素",
    "布洛芬", "阿司匹林", "对乙酰氨基酚",
    "奥美拉唑", "阿托伐他汀", "氯吡格雷", "二甲双胍",
    "硝苯地平", "卡托普利", "氨氯地平", "氯沙坦",
    "阿奇霉素", "左氧氟沙星", "甲硝唑",
    "氯雷他定", "西替利嗪", "孟鲁司特",
    "胰岛素", "优甲乐", "华法林",
]


def _extract_json_from_llm(content: str) -> dict[str, Any] | None:
    """从 LLM 回复中提取 JSON 块（兼容 ```json 包装和纯 JSON）。"""
    if "```json" in content:
        start = content.index("```json") + 7
        end = content.index("```", start) if "```" in content[start:] else len(content)
        json_str = content[start:end].strip()
    else:
        json_str = content.strip()
    try:
        return json.loads(json_str)
    except json.JSONDecodeError:
        return None


def _format_pre_diagnosis_summary(data: dict[str, str]) -> str:
    """格式化预问诊摘要为展示文本。"""
    parts = []
    chief = data.get("chief_complaint", "").strip()
    present = data.get("present_history", "").strip()
    past = data.get("past_history", "").strip()
    allergy = data.get("allergy_history", "").strip()

    if chief:
        parts.append(f"**主诉：** {chief}")
    if present:
        parts.append(f"**现病史：** {present}")
    if past:
        parts.append(f"**既往史：** {past}")
    if allergy:
        parts.append(f"**过敏史：** {allergy}")

    content = "\n\n".join(parts) if parts else "（未提供详细信息）"
    lines = [
        "📋 **预问诊摘要**",
        "---",
        content,
        "",
        "*AI 生成，仅供参考，请以医生实际问诊为准*",
    ]
    return "\n".join(lines)


def _format_prescription_for_llm(prescription_data: dict[str, Any]) -> str:
    """格式化处方数据为 LLM 可读文本。"""
    p = prescription_data.get("prescription", {})
    items = p.get("items", [])
    if not items:
        return "未找到处方明细信息。"

    lines = ["**处方详情：**", ""]
    for i, item in enumerate(items, 1):
        drug_name = item.get("drugName", "未知药物")
        usage = item.get("usageMethod", "")
        dosage = item.get("dosage", "")
        freq = item.get("frequency", "")
        remark = item.get("remark", "")

        detail_parts = []
        if usage:
            detail_parts.append(f"  用法：{usage}")
        if dosage:
            detail_parts.append(f"  剂量：{dosage}")
        if freq:
            detail_parts.append(f"  频率：{freq}")
        if remark:
            detail_parts.append(f"  备注：{remark}")

        detail = "\n".join(detail_parts) if detail_parts else "  （无详细说明）"
        lines.append(f"{i}. **{drug_name}**")
        lines.append(detail)
        lines.append("")

    diagnosis = p.get("diagnosis", "")
    advice = p.get("advice", "")
    if diagnosis:
        lines.append(f"**诊断：** {diagnosis}")
    if advice:
        lines.append(f"**医嘱：** {advice}")

    return "\n".join(lines)


def _format_allergy_warning(warnings: list[dict[str, Any]]) -> str:
    """格式化过敏警告为醒目文本。"""
    if not warnings:
        return ""

    lines = ["🚨 **⚠️ 过敏风险警告**", ""]
    for w in warnings:
        wtype = w.get("type", "")
        drug_name = w.get("drugName", "")
        target = w.get("targetName", "")
        desc = w.get("description", "")

        if wtype == "ALLERGY":
            lines.append(f"🔴 **药物「{drug_name}」** 含有过敏原「{target}」")
            lines.append(f"   {desc}")
        else:
            lines.append(f"🟡 **{drug_name}** 与 **{target}** 存在相互作用")
            lines.append(f"   {desc}")
        lines.append("")

    lines.append("**建议：** 请立即联系医生，确认是否需要调整用药方案。")
    lines.append("**注意：** 在医生确认前，请勿自行购买或服用该药物。")
    lines.append("")
    lines.append("*AI 检测仅供参考，请以医生专业判断为准。*")

    return "\n".join(lines)


def _extract_prescription_id(user_msg: str) -> int | None:
    """从用户消息中提取处方 ID。"""
    match = re.search(r'(\d+)', user_msg)
    if match:
        return int(match.group(1))
    return None


def _extract_drug_names(user_msg: str) -> list[str]:
    """从用户消息中提取已知药物名称。"""
    found = []
    for drug in _KNOWN_DRUGS:
        if drug in user_msg:
            found.append(drug)
    return found


def build_consultation_node(llm: BaseChatModel | None = None):
    """构建预问诊与处方解读意图节点。

    Args:
        llm: LangChain chat model 实例（用于预问诊对话和处方解读）

    Returns:
        node 函数：输入 state，输出 {"reply": str, "tool_calls": list[dict], "card": str|None}
    """
    # 闭包状态：当前轮次处理阶段
    _phase: str = "idle"  # idle / pre_consulting / interpreting
    _pre_diagnosis_data: dict[str, str] = {}

    # ============ 预问诊处理 ============

    async def _handle_pre_consultation(messages: list[dict], emotion: Emotion = Emotion.NEUTRAL) -> dict[str, Any]:
        """使用 LLM 处理预问诊对话。"""
        nonlocal _phase, _pre_diagnosis_data
        _phase = "pre_consulting"

        if llm is None:
            return {
                "reply": (
                    "（预问诊框架）我可以帮您整理病情摘要，方便医生了解您的情况。"
                    "请告诉我您的主要症状是什么？"
                ),
            }

        llm_messages = [
            {"role": "system", "content": inject_emotion(_PRE_CONSULT_PROMPT, emotion)},
            *messages,
        ]

        try:
            res = await llm.ainvoke(llm_messages)
            reply_content = res.content if isinstance(res.content, str) else str(res.content)
        except Exception as e:
            logger.error("预问诊 LLM 调用失败", exc_info=e)
            return {"reply": "抱歉，我暂时无法处理您的请求，请稍后重试。"}

        # 尝试解析 JSON 总结
        decision = _extract_json_from_llm(reply_content)
        if decision and decision.get("action") == "summary":
            _pre_diagnosis_data = decision
            return await _finalize_pre_diagnosis(decision, emotion)

        # 信息不足，继续追问
        return {"reply": apply_emotion_care(reply_content, emotion)}

    async def _finalize_pre_diagnosis(data: dict[str, str], emotion: Emotion = Emotion.NEUTRAL) -> dict[str, Any]:
        """完成预问诊：生成摘要 + 卡片。"""
        nonlocal _phase, _pre_diagnosis_data
        _phase = "idle"

        summary = _format_pre_diagnosis_summary(data)

        tool_calls = [{"tool": "write_pre_diagnosis", "label": "正在生成预问诊摘要..."}]

        card = card_event(
            card_type="pre_diagnosis_summary",
            title="预问诊摘要",
            action="",
            payload={
                "chiefComplaint": data.get("chief_complaint", ""),
                "presentHistory": data.get("present_history", ""),
                "pastHistory": data.get("past_history", ""),
                "allergyHistory": data.get("allergy_history", ""),
                "aiGenerated": True,
            },
        )

        reply = (
            f"{summary}\n\n"
            "这份摘要将在医生接诊时展示，帮助医生快速了解您的情况。"
            "您也可以随时告诉我需要修改的内容。"
        )
        # 问诊后主动关怀（ticket 15）：3 天回访话术
        reply = apply_emotion_care(reply, emotion, scene="follow_up_visit")

        _pre_diagnosis_data = {}
        return {"reply": reply, "tool_calls": tool_calls, "card": card}

    # ============ 处方解读处理 ============

    async def _handle_prescription_interpretation(messages: list[dict], emotion: Emotion = Emotion.NEUTRAL) -> dict[str, Any]:
        """处理处方解读。"""
        nonlocal _phase
        _phase = "interpreting"

        # 获取用户最新消息
        last_user_msg = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                last_user_msg = msg.get("content", "")
                break

        tool_calls = [{"tool": "get_prescription", "label": "正在查询处方信息..."}]

        # 尝试从消息中提取处方 ID
        prescription_id = _extract_prescription_id(last_user_msg)

        if not prescription_id:
            return {
                "reply": (
                    "好的，我来帮您解读处方。请告诉我您的处方编号，"
                    "例如「我的处方编号是 123」。"
                ),
                "tool_calls": tool_calls,
            }

        try:
            prescription_data = await call_java_tool("get_prescription", {"prescription_id": prescription_id})
        except RuntimeError as e:
            logger.warning("处方查询失败: %s", e)
            return {
                "reply": "抱歉，查询处方失败，请确认处方编号是否正确。",
                "tool_calls": tool_calls,
            }

        # 格式化处方信息
        formatted = _format_prescription_for_llm(prescription_data)

        # 用 LLM 生成通俗解释（system prompt 注入情绪语气，困惑场景强化通俗分步）
        if llm is not None:
            llm_messages = [
                {"role": "system", "content": inject_emotion(_PRESCRIPTION_INTERPRET_PROMPT, emotion)},
                {"role": "user", "content": f"请解释以下处方：\n\n{formatted}"},
            ]
            try:
                res = await llm.ainvoke(llm_messages)
                reply_content = res.content if isinstance(res.content, str) else str(res.content)
            except Exception as e:
                logger.error("处方解读 LLM 调用失败", exc_info=e)
                reply_content = formatted + "\n\n---\n*AI 解读仅供参考，请遵医嘱服用。*"
        else:
            reply_content = formatted + "\n\n---\n*AI 解读仅供参考，请遵医嘱服用。*"

        # 自动进行过敏检查
        items = prescription_data.get("prescription", {}).get("items", [])
        drug_names = [item.get("drugName", "") for item in items if item.get("drugName")]
        if drug_names:
            try:
                allergy_result = await call_java_tool("check_allergy", {"drug_names": drug_names})
                warnings = allergy_result.get("warnings", [])
                has_allergy = allergy_result.get("has_allergy_risk", False)
                if has_allergy and warnings:
                    warning_text = _format_allergy_warning(warnings)
                    reply_content = warning_text + "\n\n" + reply_content
                    reply_content += "\n\n🚫 **购药流程已被阻断**：由于检测到过敏风险，请先联系医生确认用药安全后再购买。"
                    tool_calls.append({"tool": "check_allergy", "label": "检测到过敏风险，购药已阻断"})
                else:
                    tool_calls.append({"tool": "check_allergy", "label": "过敏检查通过"})
            except RuntimeError as e:
                logger.warning("过敏检查失败: %s", e)

        _phase = "idle"
        return {"reply": apply_emotion_care(reply_content, emotion), "tool_calls": tool_calls}

    # ============ 过敏检查处理 ============

    async def _handle_allergy_check(messages: list[dict]) -> dict[str, Any]:
        """处理过敏检查。"""
        nonlocal _phase
        _phase = "interpreting"

        last_user_msg = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                last_user_msg = msg.get("content", "")
                break

        tool_calls = [{"tool": "check_allergy", "label": "正在检查过敏风险..."}]

        drug_names = _extract_drug_names(last_user_msg)

        if not drug_names:
            return {
                "reply": (
                    "好的，我来帮您检查药物过敏风险。请告诉我您想检查的药物名称，"
                    "例如「帮我检查阿莫西林是否过敏」。"
                ),
                "tool_calls": tool_calls,
            }

        try:
            allergy_result = await call_java_tool("check_allergy", {"drug_names": drug_names})
        except RuntimeError as e:
            logger.warning("过敏检查失败: %s", e)
            return {
                "reply": "抱歉，过敏检查暂时无法完成，请稍后重试或联系医生。",
                "tool_calls": tool_calls,
            }

        warnings = allergy_result.get("warnings", [])
        has_allergy = allergy_result.get("has_allergy_risk", False)

        if has_allergy and warnings:
            warning_text = _format_allergy_warning(warnings)
            reply = (
                warning_text + "\n\n"
                "🚫 **已为您阻断购药流程。** 请先联系医生确认用药方案。\n\n"
                "如果您有其他问题，随时可以问我。"
            )
        else:
            reply = (
                "✅ **未检测到过敏风险。**\n\n"
                "根据您提供的信息，这些药物与您的过敏史没有冲突。\n"
                "但请注意，过敏反应可能由多种因素引起，如有不适请及时就医。\n\n"
                "---\n"
                "*AI 检测仅供参考，请以医生专业判断为准。*"
            )

        _phase = "idle"
        return {"reply": reply, "tool_calls": tool_calls}

    # ============ 主节点函数 ============

    async def node(state: dict[str, Any]) -> dict[str, Any]:
        nonlocal _phase, _pre_diagnosis_data
        messages = state.get("messages", [])

        if not messages:
            _phase = "idle"
            _pre_diagnosis_data = {}
            return {
                "reply": (
                    "您好，我是您的健康助手。请问您需要什么帮助？\n\n"
                    "1️⃣ **预问诊** - 在看医生前，先让我帮您整理病情摘要\n"
                    "2️⃣ **处方解读** - 把处方上的药给您讲明白\n"
                    "3️⃣ **过敏检查** - 检查您的药物是否安全\n\n"
                    "请告诉我您需要哪项服务？"
                ),
            }

        # 获取用户最新消息
        last_user_msg = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                last_user_msg = msg.get("content", "")
                break

        if not last_user_msg:
            return {"reply": "请问您需要什么帮助？"}

        # 情感识别（旁路能力，ticket 15）：影响语气，困惑场景强化通俗解读
        emotion = await detect_emotion(messages, llm=llm)

        # === 场景路由 ===
        if any(kw in last_user_msg for kw in ["处方", "开药", "药怎么吃", "药品", "解读", "查处方", "看处方"]):
            return await _handle_prescription_interpretation(messages, emotion)

        if any(kw in last_user_msg for kw in ["过敏", "过不过敏", "安全吗", "检查过敏", "过敏检查"]):
            return await _handle_allergy_check(messages)

        return await _handle_pre_consultation(messages, emotion)

    return node
