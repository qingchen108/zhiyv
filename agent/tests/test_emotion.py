"""情感识别（emotion）旁路能力测试（ticket 15）。

emotion 是旁路语气能力（CONTEXT §5 / 术语表），不构成意图，不改图骨架。
本测试覆盖：
- 4 类情绪（焦虑/疼痛/困惑/满意）关键词识别准确性
- LLM 兜底判断（关键词未命中时由 LLM 判定，兼容 thinking 块列表）
- detect_emotion 失败兜底 neutral
- emotion_system_hint：各情绪 -> 语气指令映射
- inject_emotion：把指令拼到 system prompt
- apply_emotion_care：在回复尾部追加场景化关怀话术
- 主动关怀话术 care_message（挂号成功/问诊后回访/处方用完复诊）
"""

import pytest
from types import SimpleNamespace

from app.emotion import (
    Emotion,
    apply_emotion_care,
    care_message,
    detect_emotion,
    emotion_system_hint,
    inject_emotion,
)


# ============ 关键词识别准确性 ============

@pytest.mark.parametrize("text", [
    "我是不是得了绝症",
    "会不会是重病",
    "好害怕是癌症",
    "我很担心这个病治不好",
    "是不是得了什么大病",
])
async def test_detect_anxiety_by_keywords(text):
    """焦虑场景：重病/绝症/癌症/担心/害怕 -> anxiety。"""
    assert await detect_emotion([{"role": "user", "content": text}]) == Emotion.ANXIETY


@pytest.mark.parametrize("text", [
    "疼得受不了了",
    "肚子剧痛",
    "一阵一阵地疼",
    "痛得睡不着",
    "剧烈疼痛",
])
async def test_detect_pain_by_keywords(text):
    """疼痛场景：疼/痛/剧痛 -> pain。"""
    assert await detect_emotion([{"role": "user", "content": text}]) == Emotion.PAIN


@pytest.mark.parametrize("text", [
    "这个药怎么吃",
    "看不懂这个处方",
    "说明书太复杂了",
    "我不明白医生说的",
])
async def test_detect_confusion_by_keywords(text):
    """困惑场景：怎么吃/看不懂/不明白/复杂 -> confusion。"""
    assert await detect_emotion([{"role": "user", "content": text}]) == Emotion.CONFUSION


@pytest.mark.parametrize("text", [
    "谢谢你",
    "太感谢了",
    "服务很好很满意",
    "辛苦你了医生",
])
async def test_detect_satisfaction_by_keywords(text):
    """满意场景：感谢/满意/辛苦 -> satisfaction。"""
    assert await detect_emotion([{"role": "user", "content": text}]) == Emotion.SATISFACTION


async def test_detect_neutral_when_no_signal():
    """无情绪信号 -> neutral。"""
    assert await detect_emotion([{"role": "user", "content": "我想挂号"}]) == Emotion.NEUTRAL


async def test_detect_uses_last_user_message():
    """情绪识别依据最新一条用户消息。"""
    msgs = [
        {"role": "user", "content": "谢谢你"},       # 旧消息有满意信号
        {"role": "assistant", "content": "不客气"},
        {"role": "user", "content": "我再问一下怎么挂号"},  # 最新消息中性
    ]
    assert await detect_emotion(msgs) == Emotion.NEUTRAL


# ============ 优先级：疼痛 > 焦虑 > 困惑 > 满意 ============

async def test_pain_takes_priority_over_anxiety():
    """同时出现疼痛与焦虑信号时，疼痛优先（紧急程度高）。"""
    text = "肚子剧痛，我是不是得了重病"
    assert await detect_emotion([{"role": "user", "content": text}]) == Emotion.PAIN


# ============ LLM 兜底判断 ============

class FakeLLM:
    def __init__(self, content):
        self._content = content
        self.captured_messages = None

    async def ainvoke(self, messages):
        self.captured_messages = messages
        return SimpleNamespace(content=self._content)


class BrokenLLM:
    async def ainvoke(self, _messages):
        raise RuntimeError("network down")


async def test_llm_fallback_when_keywords_miss(monkeypatch):
    """关键词未命中但 LLM 判定为焦虑 -> anxiety。"""
    text = "这情况一直不见好转，我心里七上八下的"  # 无直接关键词
    fake = FakeLLM("anxiety")
    assert await detect_emotion([{"role": "user", "content": text}], llm=fake) == Emotion.ANXIETY
    # LLM 收到的 system prompt 应要求只输出情绪词
    assert fake.captured_messages is not None
    assert fake.captured_messages[0]["role"] == "system"


async def test_llm_fallback_parses_thinking_block_list():
    """thinking 模型返回块列表时，从 text 块提取情绪词。"""
    content = [
        {"type": "thinking", "thinking": "用户在感谢", "signature": "sig"},
        {"type": "text", "text": "satisfaction"},
    ]
    text = "麻烦你了"  # 无强关键词
    assert await detect_emotion([{"role": "user", "content": text}], llm=FakeLLM(content)) == Emotion.SATISFACTION


async def test_llm_fallback_unknown_falls_back_to_neutral():
    """LLM 返回未知情绪词 -> neutral。"""
    text = "今天天气不错"
    assert await detect_emotion([{"role": "user", "content": text}], llm=FakeLLM("xyz")) == Emotion.NEUTRAL


async def test_llm_failure_falls_back_to_neutral():
    """LLM 调用异常 -> neutral（不拖垮对话）。"""
    text = "今天天气不错"
    assert await detect_emotion([{"role": "user", "content": text}], llm=BrokenLLM()) == Emotion.NEUTRAL


async def test_keywords_hit_skips_llm():
    """关键词命中时不调用 LLM（节省开销）。"""
    text = "我是不是得了重病"

    class SpyLLM:
        called = False

        async def ainvoke(self, _messages):
            SpyLLM.called = True
            return SimpleNamespace(content="neutral")

    assert await detect_emotion([{"role": "user", "content": text}], llm=SpyLLM()) == Emotion.ANXIETY
    assert SpyLLM.called is False


# ============ emotion_system_hint：语气指令 ============

def test_emotion_system_hint_neutral_is_empty():
    """neutral 不附加语气指令。"""
    assert emotion_system_hint(Emotion.NEUTRAL) == ""


def test_emotion_system_hint_anxiety_contains_reassure():
    hint = emotion_system_hint(Emotion.ANXIETY)
    assert "安抚" in hint or "理性" in hint


def test_emotion_system_hint_pain_contains_urgent():
    hint = emotion_system_hint(Emotion.PAIN)
    assert "优先" in hint or "紧急" in hint


def test_emotion_system_hint_confusion_contains_plain():
    hint = emotion_system_hint(Emotion.CONFUSION)
    assert "通俗" in hint or "分步" in hint


def test_emotion_system_hint_satisfaction_contains_warm():
    hint = emotion_system_hint(Emotion.SATISFACTION)
    assert "温馨" in hint


# ============ inject_emotion ============

def test_inject_emotion_appends_hint_to_system_prompt():
    base = "你是医疗助手。"
    result = inject_emotion(base, Emotion.ANXIETY)
    assert result.startswith("你是医疗助手。")
    assert "安抚" in result or "理性" in result


def test_inject_emotion_neutral_returns_base_unchanged():
    base = "你是医疗助手。"
    assert inject_emotion(base, Emotion.NEUTRAL) == base


# ============ care_message：主动关怀话术 ============

def test_care_message_registration_success():
    msg = care_message("registration_success")
    assert "挂号" in msg
    assert "提醒" in msg or "就诊" in msg


def test_care_message_follow_up_visit():
    msg = care_message("follow_up_visit")
    assert "复诊" in msg or "回访" in msg


def test_care_message_prescription_refill():
    msg = care_message("prescription_refill")
    assert "处方" in msg or "复诊" in msg or "购药" in msg


def test_care_message_unknown_scene_is_empty():
    assert care_message("unknown") == ""


# ============ apply_emotion_care ============

def test_apply_emotion_care_satisfaction_appends_followup():
    """满意场景：回复尾部追加后续引导（用药提醒/复诊）。"""
    reply = "不客气，已为您挂号成功。"
    result = apply_emotion_care(reply, Emotion.SATISFACTION, scene="registration_success")
    assert result.startswith(reply)
    assert result != reply  # 追加了关怀话术


def test_apply_emotion_care_neutral_returns_reply_unchanged():
    """neutral 且无 scene -> 不追加。"""
    reply = "已为您查询到排班。"
    assert apply_emotion_care(reply, Emotion.NEUTRAL) == reply


def test_apply_emotion_care_neutral_with_scene_appends_care():
    """neutral 但有 scene（如挂号成功）-> 仍追加主动关怀。"""
    reply = "挂号草稿已创建。"
    result = apply_emotion_care(reply, Emotion.NEUTRAL, scene="registration_success")
    assert result.startswith(reply)
    assert "挂号" in result or "就诊" in result or "提醒" in result


def test_apply_emotion_care_anxiety_no_scene_returns_reply_unchanged():
    """焦虑但无 scene -> 不追加（焦虑靠 system prompt 语气调整，不靠尾部话术）。"""
    reply = "已为您推荐科室。"
    assert apply_emotion_care(reply, Emotion.ANXIETY) == reply
