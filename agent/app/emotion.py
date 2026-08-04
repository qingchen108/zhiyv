"""情感识别（emotion）旁路能力（ticket 15，CONTEXT §5 / 术语表）。

emotion 是旁路语气能力，不构成意图，不进 LangGraph 图骨架：
- 在各意图节点入口检测用户情绪，把语气指令注入 system prompt（影响 LLM 话术风格）
- 在场景化回复尾部追加主动关怀话术（挂号成功提醒、问诊后回访、处方用完复诊）

识别策略：关键词优先（快速、零开销），关键词未命中时由 LLM 兜底判断；
LLM 调用失败或返回未知词一律兜底 neutral，不拖垮对话主流程。
"""

import logging
from enum import Enum
from typing import Any

from langchain_core.language_models.chat_models import BaseChatModel

logger = logging.getLogger(__name__)


class Emotion(str, Enum):
    """用户情绪标签。NEUTRAL 为兜底（无显著情绪信号）。"""

    ANXIETY = "anxiety"        # 焦虑：安抚 + 理性分析 + 引导就医
    PAIN = "pain"              # 疼痛：优先处理 + 紧急建议 + 快速导诊
    CONFUSION = "confusion"    # 困惑：通俗解读 + 分步说明
    SATISFACTION = "satisfaction"  # 满意：温馨回应 + 引导后续
    NEUTRAL = "neutral"        # 中性：不附加语气调整


# 关键词 -> 情绪（按检测顺序排列；疼痛优先级最高，与紧急程度一致）
_EMOTION_KEYWORDS: list[tuple[Emotion, tuple[str, ...]]] = [
    # 疼痛优先（紧急程度最高，需优先处理/快速导诊）
    (Emotion.PAIN, ("疼", "痛", "剧痛", "绞痛", "刺痛", "胀痛", "阵痛")),
    # 焦虑（重病担忧，需安抚 + 理性分析）
    (Emotion.ANXIETY, (
        "重病", "大病", "绝症", "癌症", "肿瘤", "治不好", "好不了",
        "害怕", "担心", "焦虑", "紧张", "会不会是", "是不是得了",
    )),
    # 困惑（看不懂，需通俗解读 + 分步说明）
    (Emotion.CONFUSION, (
        "怎么吃", "看不懂", "不明白", "不懂", "什么意思", "复杂",
        "说明书", "搞不清", "弄不明白",
    )),
    # 满意（感谢，需温馨回应 + 引导后续）
    (Emotion.SATISFACTION, ("谢谢", "感谢", "辛苦", "满意", "太好了", "谢谢啦")),
]

# LLM 兜底用的情绪词候选（用于本地匹配 LLM 返回）
_LLM_EMOTION_WORDS: dict[str, Emotion] = {
    "anxiety": Emotion.ANXIETY,
    "pain": Emotion.PAIN,
    "confusion": Emotion.CONFUSION,
    "satisfaction": Emotion.SATISFACTION,
    "neutral": Emotion.NEUTRAL,
}

# 各情绪对应的语气指令（注入 system prompt，影响 LLM 话术风格）
_EMOTION_HINTS: dict[Emotion, str] = {
    Emotion.ANXIETY: (
        "【语气调整】用户表现出焦虑情绪，请用温和安抚的语气回复：先共情安抚，"
        "再理性分析症状可能性（避免过度引导向重病），最后引导就医或进一步检查。"
    ),
    Emotion.PAIN: (
        "【语气调整】用户正在经历疼痛，请优先处理：简洁明确地给出紧急建议与快速导诊，"
        "避免冗长追问，必要时建议立即就医。"
    ),
    Emotion.CONFUSION: (
        "【语气调整】用户感到困惑，请用通俗易懂的语言分步说明，避免专业术语，"
        "关键步骤用编号列出，必要时举例。"
    ),
    Emotion.SATISFACTION: (
        "【语气调整】用户表达满意/感谢，请温馨回应，并自然引导后续（用药提醒、复诊等）。"
    ),
    Emotion.NEUTRAL: "",
}

# 场景化主动关怀话术（场景 key -> 话术）
_CARE_MESSAGES: dict[str, str] = {
    "registration_success": (
        "\n\n💡 **温馨提示：** 就诊当天请提前 15 分钟到达，记得带好身份证和医保卡。"
        "如需改期，请提前在「我的挂号」中操作。"
    ),
    "follow_up_visit": (
        "\n\n💡 **健康关怀：** 问诊后 3 天内如症状未缓解或加重，请及时复诊。"
        "我也可以帮您设置用药提醒，按时服药有助于康复。"
    ),
    "prescription_refill": (
        "\n\n💡 **复诊提醒：** 您的处方药即将用完，建议提前复诊开具新处方。"
        "需要的话我可以帮您对比药店价格或预约复诊。"
    ),
}


def _extract_last_user_message(messages: list[dict[str, str]]) -> str:
    """取最新一条 user 消息内容（情绪识别依据最新消息）。"""
    for msg in reversed(messages):
        if msg.get("role") == "user":
            return msg.get("content", "")
    return ""


def _detect_by_keywords(text: str) -> Emotion | None:
    """关键词匹配（按优先级顺序，命中即返回）。"""
    for emotion, keywords in _EMOTION_KEYWORDS:
        if any(kw in text for kw in keywords):
            return emotion
    return None


def _extract_emotion_from_llm(content: Any) -> Emotion:
    """从 LLM 响应提取情绪词，兼容 str 与 thinking 模型块列表。"""
    if isinstance(content, str):
        text = content
    elif isinstance(content, list):
        text = " ".join(
            block.get("text", "") if isinstance(block, dict) else str(block)
            for block in content
        )
    else:
        text = str(content)
    lowered = text.lower()
    for word, emotion in _LLM_EMOTION_WORDS.items():
        if word in lowered:
            return emotion
    logger.warning("情绪识别 LLM 结果无法解析，兜底 neutral: content=%r", content)
    return Emotion.NEUTRAL


async def detect_emotion(
    messages: list[dict[str, str]],
    llm: BaseChatModel | None = None,
) -> Emotion:
    """识别用户情绪。

    策略：关键词优先（快速零开销）；关键词未命中且有 LLM 时，由 LLM 兜底判断。
    LLM 调用失败或返回未知词一律兜底 neutral，不拖垮对话主流程。

    Args:
        messages: 对话消息列表（取最新一条 user 消息分析）
        llm: 可选 LLM 实例（关键词未命中时兜底判断）

    Returns:
        情绪标签（Emotion.NEUTRAL 为兜底）
    """
    text = _extract_last_user_message(messages)
    if not text:
        return Emotion.NEUTRAL

    # 1. 关键词优先
    hit = _detect_by_keywords(text)
    if hit is not None:
        return hit

    # 2. LLM 兜底（无 LLM 则 neutral）
    if llm is None:
        return Emotion.NEUTRAL

    try:
        res = await llm.ainvoke([
            {
                "role": "system",
                "content": (
                    "你是智愈医疗助手的情绪识别器。只输出一个英文情绪词，不要输出其他内容：\n"
                    "anxiety（焦虑/担忧重病）、pain（疼痛）、confusion（困惑/看不懂）、"
                    "satisfaction（感谢/满意）、neutral（无明显情绪）。"
                    "依据用户最新消息判断，历史消息仅供参考。"
                ),
            },
            {"role": "user", "content": text},
        ])
        return _extract_emotion_from_llm(res.content)
    except Exception as e:  # noqa: BLE001 -- 情绪识别失败兜底 neutral
        logger.warning("情绪识别 LLM 调用失败，兜底 neutral: %s", e)
        return Emotion.NEUTRAL


def emotion_system_hint(emotion: Emotion) -> str:
    """返回情绪对应的语气指令（注入 system prompt）。NEUTRAL 返回空串。"""
    return _EMOTION_HINTS.get(emotion, "")


def inject_emotion(system_prompt: str, emotion: Emotion) -> str:
    """把情绪语气指令注入 system prompt。

    NEUTRAL 时原样返回（不改 prompt）。非 NEUTRAL 时在末尾追加语气指令段落。
    """
    hint = emotion_system_hint(emotion)
    if not hint:
        return system_prompt
    return f"{system_prompt}\n\n{hint}"


def care_message(scene: str) -> str:
    """返回场景化主动关怀话术。未知场景返回空串。

    场景：
    - registration_success：挂号成功提醒（就诊准备）
    - follow_up_visit：问诊后 3 天回访（症状未缓解复诊）
    - prescription_refill：处方用完复诊提醒
    """
    return _CARE_MESSAGES.get(scene, "")


def apply_emotion_care(
    reply: str,
    emotion: Emotion,
    scene: str | None = None,
) -> str:
    """在回复尾部追加场景化关怀话术。

    规则：
    - 满意（satisfaction）：追加后续引导（用药提醒/复诊），scene 可选指定具体话术
    - neutral + 有 scene：追加该场景的主动关怀话术
    - 其他情绪（焦虑/疼痛/困惑）无 scene：不追加（靠 system prompt 语气调整）
    - 其他情绪 + 有 scene：追加该场景话术（情绪影响语气，场景话术影响尾部引导）

    Args:
        reply: 意图节点生成的回复文本
        emotion: 检测到的情绪
        scene: 可选场景 key（见 care_message），None 表示无主动关怀场景

    Returns:
        拼接后的回复文本（无追加时原样返回）
    """
    if scene is not None:
        care = care_message(scene)
        if care:
            return reply + care

    # 无 scene 时，仅满意情绪追加通用后续引导
    if emotion == Emotion.SATISFACTION:
        return reply + care_message("follow_up_visit")

    return reply
