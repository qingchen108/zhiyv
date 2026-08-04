"""购药（pharmacy）意图节点编排（ticket 14）。

处理流程：
1. 用户表达购药意图 -> 确定 prescriptionId（消息提取或查病历取最近 ACTIVE 处方）
2. 查处方明细 -> 逐药调 query_pharmacy_stock 聚合对比（ADR-0016，单 drug_id 多次调用）
3. LLM 生成药店对比文案（无 LLM 降级模板）-> 缓存对比结果，进入 awaiting_choice
4. 用户选药店 -> 调 create_order_draft -> 生成 order_confirm 确认卡片
5. 文案提示"确认后将自动为您设置用药提醒"（ADR-0017，confirm 同事务自动生成提醒）

节点函数被 intents.py 的 build_intent_node("pharmacy") 调用。
"""

import logging
import re
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel

from app.emotion import Emotion, apply_emotion_care, detect_emotion, inject_emotion
from app.tool_client import call_java_tool
from app.sse import card_event

logger = logging.getLogger(__name__)

# 购药引导提示
_PHARMACY_GREETING = (
    "好的，我来帮您对比药店价格并下单。\n\n"
    "请告诉我您要购买哪张处方的药：\n"
    "- 直接说处方编号（如「买处方 123 的药」）\n"
    "- 或说「买最近的药」，我帮您查最近一张处方\n\n"
    "确认购药后会自动为您设置用药提醒。"
)

# 药店选择提示
_PHARMACY_CHOICE_PROMPT = "请从以上药店中选择一家（回复编号），或告诉我其他需求。"

# 药店对比系统 prompt
_PHARMACY_COMPARE_PROMPT = """你是一个购药助手，负责用通俗易懂的语言为患者对比各药店的购药方案。

你会收到一处方含多种药品，每种药品在多家药店有库存、价格、距离和配送时效数据。
请生成一段对比文案，要求：
1. 先概述这张处方含哪些药、总共几家药店可供选择
2. 按推荐顺序列出各药店（价格/距离/配送时效综合考量），每家用一句话概括特点
3. 结尾提示患者选择药店编号下单，并说明确认后会自动设置用药提醒

语气亲切专业，不要逐项罗列所有数字，重点帮患者做决策。不要输出 JSON 或代码块。"""


# 中文数字映射（药店选择提取，与 registration 一致）
_CN_NUM = {"零": 0, "一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
           "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}


def _extract_prescription_id(user_msg: str) -> int | None:
    """从用户消息中提取处方 ID（取第一个数字）。"""
    match = re.search(r"(\d+)", user_msg)
    if match:
        return int(match.group(1))
    return None


def _is_recent_prescription_request(user_msg: str) -> bool:
    """判断用户是否要求购买最近处方（无具体编号）。"""
    keywords = ["最近", "最新", "上次", "上回", "那张", "我的处方"]
    return any(kw in user_msg for kw in keywords)


def _pick_latest_active_prescription(record: dict[str, Any]) -> int | None:
    """从病历聚合中取最近一张 ACTIVE 处方的 id（prescriptions 已按 createdAt 降序）。"""
    prescriptions = record.get("prescriptions") or []
    for p in prescriptions:
        if p.get("status") == "ACTIVE":
            pid = p.get("id")
            if pid is not None:
                return int(pid)
    return None


def _format_pharmacy_comparison(prescription: dict[str, Any],
                                stock_by_drug: dict[int, list[dict[str, Any]]]) -> str:
    """格式化药店对比为模板文案（LLM 不可用时的降级方案）。"""
    p = prescription.get("prescription", prescription)
    items = p.get("items", [])
    if not items:
        return "未找到处方明细，无法对比药店。"

    # 收集所有出现的药店（pharmacyId 去重，保持出现顺序）
    seen_pharmacies: dict[int, dict[str, Any]] = {}
    for stock_list in stock_by_drug.values():
        for s in stock_list:
            pid = s.get("pharmacyId")
            if pid is not None and pid not in seen_pharmacies:
                seen_pharmacies[pid] = {
                    "pharmacyId": pid,
                    "pharmacyName": s.get("pharmacyName", "未知药店"),
                    "pharmacyAddress": s.get("pharmacyAddress", ""),
                }

    if not seen_pharmacies:
        return "当前没有药店可供选择，请稍后再试。"

    drug_names = [item.get("drugName", f"药品{item.get('drugId')}") for item in items]
    lines = [f"**处方含 {len(drug_names)} 种药：**{ '、'.join(drug_names) }", ""]
    lines.append(f"**共找到 {len(seen_pharmacies)} 家药店可供选择：**\n")
    for i, (pid, info) in enumerate(seen_pharmacies.items(), 1):
        name = info["pharmacyName"]
        addr = info["pharmacyAddress"]
        addr_suffix = f"（{addr}）" if addr else ""
        lines.append(f"{i}. **{name}**{addr_suffix}")

    lines.append("")
    lines.append(_PHARMACY_CHOICE_PROMPT)
    return "\n".join(lines)


def _build_compare_context(prescription: dict[str, Any],
                           stock_by_drug: dict[int, list[dict[str, Any]]]) -> str:
    """构建供 LLM 生成对比文案的上下文文本。"""
    p = prescription.get("prescription", prescription)
    items = p.get("items", [])
    diagnosis = p.get("diagnosis", "")

    lines = []
    if diagnosis:
        lines.append(f"诊断：{diagnosis}")
    lines.append(f"处方含 {len(items)} 种药：")
    for item in items:
        drug_name = item.get("drugName", f"药品{item.get('drugId')}")
        dosage = item.get("dosage", "")
        frequency = item.get("frequency", "")
        lines.append(f"  - {drug_name}（{dosage}，{frequency}）")

    lines.append("")
    lines.append("各药店库存/价格/距离/配送时效对比：")
    seen_pharmacies: dict[int, dict[str, Any]] = {}
    for drug_id, stock_list in stock_by_drug.items():
        drug_name = next(
            (it.get("drugName") for it in items if it.get("drugId") == drug_id),
            f"药品{drug_id}")
        for s in stock_list:
            pid = s.get("pharmacyId")
            if pid is not None and pid not in seen_pharmacies:
                seen_pharmacies[pid] = {
                    "pharmacyName": s.get("pharmacyName", "未知药店"),
                    "pharmacyAddress": s.get("pharmacyAddress", ""),
                }
            lines.append(
                f"  药店「{s.get('pharmacyName', '未知')}」: "
                f"{drug_name} 价格 {s.get('price', '?')}元, "
                f"库存 {s.get('stock', 0)}, "
                f"距离 {s.get('distanceM', '?')}米, "
                f"配送 {s.get('deliveryEtaMin', '?')}分钟"
            )

    return "\n".join(lines)


def _extract_pharmacy_choice(user_message: str,
                             pharmacy_options: list[dict[str, Any]]) -> dict[str, Any] | None:
    """从用户消息中提取药店选择（编号/中文数字，与 registration 排班选择一致）。"""
    text = user_message.strip()

    # 先尝试中文数字
    cn_match = re.search(r"[第]?([零一二三四五六七八九十]+?)([个位家])?", text)
    if cn_match:
        cn_num_str = cn_match.group(1)
        if cn_num_str in _CN_NUM:
            idx = _CN_NUM[cn_num_str]
            if 1 <= idx <= len(pharmacy_options):
                return pharmacy_options[idx - 1]

    # 再尝试阿拉伯数字
    match = re.search(r"[第]?(\d+)[个位家]?", text)
    if match:
        idx = int(match.group(1))
        if 1 <= idx <= len(pharmacy_options):
            return pharmacy_options[idx - 1]

    return None


async def _resolve_prescription_id(user_msg: str) -> tuple[int | None, list[dict] | None]:
    """确定 prescriptionId：消息提取优先，否则查病历取最近 ACTIVE 处方。

    Returns:
        (prescription_id, tool_calls) -- tool_calls 记录本轮调用的工具轨迹
    """
    tool_calls: list[dict] = []
    pid = _extract_prescription_id(user_msg)
    if pid is not None:
        return pid, tool_calls

    # 无编号且未明确要求最近 -> 不主动查病历，提示用户
    if not _is_recent_prescription_request(user_msg):
        return None, None

    tool_calls.append({"tool": "get_medical_record", "label": "正在查询您的病历取最近处方..."})
    try:
        record_data = await call_java_tool("get_medical_record", {})
    except RuntimeError as e:
        logger.warning("病历查询失败: %s", e)
        return None, tool_calls

    record = record_data.get("record", record_data)
    pid = _pick_latest_active_prescription(record)
    return pid, tool_calls


async def _load_prescription_and_stocks(prescription_id: int) -> tuple[dict | None, dict[int, list] | None, list[dict]]:
    """查询处方明细 + 逐药查药店库存聚合。

    Returns:
        (prescription_data, stock_by_drug, tool_calls)
    """
    tool_calls: list[dict] = []
    tool_calls.append({"tool": "get_prescription", "label": "正在查询处方明细..."})
    try:
        prescription_data = await call_java_tool("get_prescription", {"prescription_id": prescription_id})
    except RuntimeError as e:
        logger.warning("处方查询失败: %s", e)
        return None, None, tool_calls

    items = prescription_data.get("prescription", {}).get("items", [])
    if not items:
        return prescription_data, {}, tool_calls

    # 逐药查药店库存（ADR-0016，单 drug_id 多次调用后聚合）
    stock_by_drug: dict[int, list] = {}
    for item in items:
        drug_id = item.get("drugId")
        if drug_id is None:
            continue
        tool_calls.append({
            "tool": "query_pharmacy_stock",
            "label": f"正在查询 {item.get('drugName', '药品')} 的药店库存...",
        })
        try:
            stock_result = await call_java_tool("query_pharmacy_stock", {"drug_id": drug_id})
            stock_by_drug[int(drug_id)] = stock_result.get("stocks", [])
        except RuntimeError as e:
            logger.warning("药店库存查询失败 drug_id=%s: %s", drug_id, e)
            stock_by_drug[int(drug_id)] = []

    return prescription_data, stock_by_drug, tool_calls


def build_pharmacy_node(llm: BaseChatModel | None = None):
    """构建购药意图节点。

    Args:
        llm: LangChain chat model 实例（用于生成药店对比文案）；echo 模式或无 LLM 时传 None，
             降级为模板文案。

    Returns:
        node 函数：输入 state，输出 {"reply": str, "tool_calls": list[dict], "card": str|None}
    """
    # 闭包状态（与 registration/consultation 一致的单例模式）
    _phase: str = "idle"  # idle / awaiting_choice
    _prescription_id: int | None = None
    _pharmacy_options: list[dict[str, Any]] = []  # 缓存对比阶段的药店列表供选择

    async def node(state: dict[str, Any]) -> dict[str, Any]:
        nonlocal _phase, _prescription_id, _pharmacy_options
        messages = state.get("messages", [])

        if not messages:
            _phase = "idle"
            return {"reply": _PHARMACY_GREETING}

        # 获取用户最新消息
        last_user_msg = ""
        for msg in reversed(messages):
            if msg.get("role") == "user":
                last_user_msg = msg.get("content", "")
                break

        if not last_user_msg:
            return {"reply": _PHARMACY_GREETING}

        # 情感识别（旁路能力，ticket 15）：影响对比文案语气
        emotion = await detect_emotion(messages, llm=llm)

        # === awaiting_choice 阶段：用户选药店 ===
        if _phase == "awaiting_choice" and _pharmacy_options:
            selected = _extract_pharmacy_choice(last_user_msg, _pharmacy_options)
            if selected:
                pharmacy_id = selected.get("pharmacyId")
                return await _create_order_draft(_prescription_id, pharmacy_id, selected, emotion)
            # 无效选择，重新提示
            return {"reply": _PHARMACY_CHOICE_PROMPT}

        # === idle 阶段：确定处方 + 查库存对比 ===
        prescription_id, resolve_tool_calls = await _resolve_prescription_id(last_user_msg)

        if resolve_tool_calls is None:
            # 未给编号且未要求最近 -> 引导
            return {
                "reply": (
                    "好的，我来帮您购药。请告诉我处方编号（如「买处方 123 的药」），\n"
                    "或说「买最近的药」我帮您查最近一张处方。"
                ),
            }

        if prescription_id is None:
            _phase = "idle"
            reply = "⚠️ 未能找到可用的处方。"
            if resolve_tool_calls:
                reply += "请确认您有已生效的处方，或直接告诉我处方编号。"
            else:
                reply += "请直接告诉我处方编号（如「买处方 123 的药」）。"
            return {"reply": reply, "tool_calls": resolve_tool_calls}

        _prescription_id = prescription_id

        # 查处方明细 + 逐药查库存
        prescription_data, stock_by_drug, load_tool_calls = await _load_prescription_and_stocks(prescription_id)
        tool_calls = (resolve_tool_calls or []) + load_tool_calls

        if prescription_data is None:
            _phase = "idle"
            return {
                "reply": "⚠️ 处方查询失败，请确认处方编号是否正确后重试。",
                "tool_calls": tool_calls,
            }

        if not stock_by_drug or all(not v for v in stock_by_drug.values()):
            _phase = "idle"
            return {
                "reply": "⚠️ 当前没有药店有该处方药品的库存，请稍后再试。",
                "tool_calls": tool_calls,
            }

        # 收集药店选项（去重，保持顺序）缓存供选择阶段用
        seen: dict[int, dict[str, Any]] = {}
        for stock_list in stock_by_drug.values():
            for s in stock_list:
                pid = s.get("pharmacyId")
                if pid is not None and pid not in seen:
                    seen[pid] = {
                        "pharmacyId": pid,
                        "pharmacyName": s.get("pharmacyName", "未知药店"),
                        "pharmacyAddress": s.get("pharmacyAddress", ""),
                    }
        _pharmacy_options = list(seen.values())

        # LLM 生成对比文案，无 LLM 降级模板（system prompt 注入情绪语气指令）
        if llm is not None:
            context = _build_compare_context(prescription_data, stock_by_drug)
            try:
                res = await llm.ainvoke([
                    {"role": "system", "content": inject_emotion(_PHARMACY_COMPARE_PROMPT, emotion)},
                    {"role": "user", "content": context},
                ])
                reply_content = res.content if isinstance(res.content, str) else str(res.content)
            except Exception as e:
                logger.error("药店对比 LLM 调用失败", exc_info=e)
                reply_content = _format_pharmacy_comparison(prescription_data, stock_by_drug)
        else:
            reply_content = _format_pharmacy_comparison(prescription_data, stock_by_drug)

        _phase = "awaiting_choice"
        return {"reply": apply_emotion_care(reply_content, emotion), "tool_calls": tool_calls}

    async def _create_order_draft(prescription_id: int | None,
                                   pharmacy_id: int | None,
                                   selected: dict[str, Any],
                                   emotion: Emotion = Emotion.NEUTRAL) -> dict[str, Any]:
        """用户选定药店 -> 创建购药草稿 -> 生成确认卡片。"""
        nonlocal _phase, _pharmacy_options
        tool_calls = [{"tool": "create_order_draft", "label": "正在创建购药草稿..."}]

        try:
            draft = await call_java_tool("create_order_draft", {
                "prescription_id": prescription_id,
                "pharmacy_id": pharmacy_id,
            })
        except RuntimeError as e:
            logger.warning("创建购药草稿失败: %s", e)
            _phase = "idle"
            _pharmacy_options = []
            return {
                "reply": (
                    f"⚠️ 创建购药草稿失败：{e}\n\n"
                    "请稍后重试，或重新选择药店。"
                ),
                "tool_calls": tool_calls,
            }

        # 草稿创建成功 -> 生成 order_confirm 卡片（payload 为草稿响应权威 JSON）
        _phase = "idle"
        _pharmacy_options = []

        card = card_event(
            card_type="order_confirm",
            title="确认购药信息",
            action="/api/c/orders/confirm",
            payload={
                "draftKey": draft.get("draftKey"),
                "confirmToken": draft.get("confirmToken"),
                "prescriptionId": draft.get("prescriptionId"),
                "pharmacyId": draft.get("pharmacyId"),
                "pharmacyName": draft.get("pharmacyName", selected.get("pharmacyName", "")),
                "items": draft.get("items", []),
                "totalAmount": draft.get("totalAmount"),
            },
        )

        pharmacy_name = draft.get("pharmacyName", selected.get("pharmacyName", ""))
        reply = (
            f"已为您创建购药草稿：\n"
            f"💊 **{pharmacy_name}**\n"
            f"💰 总价：{draft.get('totalAmount', '?')} 元\n\n"
            "请点击下方卡片确认购药。\n"
            "✅ 确认后将自动为您设置用药提醒。"
        )
        # 处方用完复诊提醒（ticket 15 主动关怀）
        reply = apply_emotion_care(reply, emotion, scene="prescription_refill")

        return {"reply": reply, "tool_calls": tool_calls, "card": card}

    return node
