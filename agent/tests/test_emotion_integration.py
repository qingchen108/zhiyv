"""情感识别集成到各意图节点的话术风格切换测试（ticket 15）。

验证 emotion 旁路能力在各意图节点生效：
- triage：焦虑/疼痛 -> system prompt 注入语气指令；疼痛触发紧急建议
- consultation：困惑 -> 处方解读 system prompt 注入通俗分步指令
- pharmacy：焦虑 -> 对比文案 system prompt 注入安抚指令
- registration：挂号成功 -> 回复追加就诊准备提醒关怀话术
- 满意情绪 -> 回复尾部追加后续引导
"""

import pytest
from types import SimpleNamespace

from app.emotion import Emotion
from app.intents import build_intent_node


class CaptureLLM:
    """记录 system prompt 的 mock LLM，按预设内容回复。"""

    def __init__(self, content):
        self._content = content
        self.captured_system = ""

    async def ainvoke(self, messages):
        self.captured_system = messages[0]["content"]
        return SimpleNamespace(content=self._content)


# ============ triage：焦虑 -> system prompt 注入安抚语气 ============

async def test_triage_anxiety_injects_reassure_hint():
    """焦虑场景（"是不是得了重病"）：导诊 system prompt 应含安抚语气指令。"""
    llm = CaptureLLM("请告诉我症状持续多久了？")
    node = build_intent_node("triage", llm=llm)
    await node({"messages": [{"role": "user", "content": "我是不是得了重病"}], "intent": "triage", "reply": "", "tool_calls": []})
    assert "安抚" in llm.captured_system or "理性" in llm.captured_system


async def test_triage_pain_triggers_emergency_advice():
    """疼痛场景（剧烈疼痛）：优先处理 + 紧急建议（不追问，直接建议就医）。"""
    llm = CaptureLLM("请描述疼痛部位")
    node = build_intent_node("triage", llm=llm)
    result = await node({"messages": [{"role": "user", "content": "肚子剧痛受不了了"}], "intent": "triage", "reply": "", "tool_calls": []})
    # 疼痛触发紧急分支，不调用 LLM 追问
    assert "立即就医" in result["reply"] or "急诊" in result["reply"]


async def test_triage_neutral_no_emotion_hint():
    """中性场景：system prompt 不含语气调整指令。"""
    llm = CaptureLLM("请描述症状")
    node = build_intent_node("triage", llm=llm)
    await node({"messages": [{"role": "user", "content": "我想咨询挂号"}], "intent": "triage", "reply": "", "tool_calls": []})
    assert "语气调整" not in llm.captured_system


# ============ consultation：困惑 -> 处方解读注入通俗分步指令 ============

async def test_consultation_confusion_injects_plain_hint(monkeypatch):
    """困惑场景（"这个药怎么吃"）：处方解读 system prompt 应含通俗/分步指令。"""
    llm = CaptureLLM("这种药每天吃 2 次，每次 1 片")
    node = build_intent_node("consultation", llm=llm)

    async def fake_call(name, arguments):
        if name == "get_prescription":
            return {"prescription": {"items": [{"drugName": "阿莫西林", "usageMethod": "口服", "dosage": "0.5g", "frequency": "每日2次"}]}}
        if name == "check_allergy":
            return {"warnings": [], "has_allergy_risk": False}
        return {}

    monkeypatch.setattr("app.consultation.call_java_tool", fake_call)

    # "药怎么吃" 命中困惑关键词 + 处方解读场景路由；带处方编号确保走 LLM 解读
    await node({"messages": [{"role": "user", "content": "处方 123 这个药怎么吃"}], "intent": "consultation", "reply": "", "tool_calls": []})
    assert "通俗" in llm.captured_system or "分步" in llm.captured_system


async def test_consultation_anxiety_pre_consult_injects_reassure():
    """焦虑场景走预问诊：system prompt 应含安抚指令。"""
    llm = CaptureLLM("请告诉我主要症状")
    node = build_intent_node("consultation", llm=llm)
    # 不含处方/过敏关键词 -> 走预问诊；含"重病" -> 焦虑
    await node({"messages": [{"role": "user", "content": "我是不是得了重病，很担心"}], "intent": "consultation", "reply": "", "tool_calls": []})
    assert "安抚" in llm.captured_system or "理性" in llm.captured_system


# ============ pharmacy：焦虑 -> 对比文案注入安抚指令 ============

async def test_pharmacy_anxiety_injects_reassure_hint(monkeypatch):
    """焦虑场景购药：对比文案 system prompt 应含安抚指令。"""
    llm = CaptureLLM("已为您对比药店")
    node = build_intent_node("pharmacy", llm=llm)

    async def fake_call(name, arguments):
        if name == "get_prescription":
            return {"prescription": {"items": [{"drugId": 1, "drugName": "阿莫西林"}]}}
        if name == "query_pharmacy_stock":
            return {"stocks": [{"pharmacyId": 10, "pharmacyName": "健康大药房", "price": 25.0, "stock": 100}]}
        return {}

    monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

    # "重病"焦虑 + "买处方 123 的药"
    await node({"messages": [{"role": "user", "content": "我是不是得了重病，帮我买处方 123 的药"}], "intent": "pharmacy", "reply": "", "tool_calls": []})
    assert "安抚" in llm.captured_system or "理性" in llm.captured_system


# ============ registration：挂号成功 -> 追加就诊准备提醒 ============

async def test_registration_success_appends_care(monkeypatch):
    """挂号草稿创建成功 -> 回复尾部追加就诊准备提醒关怀话术。"""
    node = build_intent_node("registration")

    # 第一轮：查排班
    async def fake_call_first(name, arguments):
        return {"schedules": [{"scheduleId": 5, "departmentName": "内科", "doctorName": "张医生",
                                "scheduleDate": "2026-08-05", "timePeriod": "MORNING", "timeRange": "08:00-12:00",
                                "remainingSlots": 3}]}

    monkeypatch.setattr("app.registration.call_java_tool", fake_call_first)
    await node({"messages": [{"role": "user", "content": "挂号"}], "intent": "registration", "reply": "", "tool_calls": []})

    # 第二轮：选择第 1 个 -> 创建草稿成功 -> 应含就诊准备提醒
    async def fake_call_second(name, arguments):
        return {"scheduleId": 5, "confirmToken": "tok", "familyMemberId": 1,
                "doctorName": "张医生", "departmentName": "内科",
                "scheduleDate": "2026-08-05", "timePeriod": "MORNING", "timeRange": "08:00-12:00",
                "visitorName": "本人"}

    monkeypatch.setattr("app.registration.call_java_tool", fake_call_second)
    result = await node({"messages": [{"role": "user", "content": "选第1个"}], "intent": "registration", "reply": "", "tool_calls": []})
    assert "就诊" in result["reply"] or "提醒" in result["reply"] or "提前" in result["reply"]


# ============ 满意情绪 -> 回复尾部追加后续引导 ============

async def test_triage_satisfaction_appends_followup():
    """满意场景（感谢）：导诊回复尾部追加后续引导（用药提醒/复诊）。"""
    llm = CaptureLLM("不客气，已为您推荐科室。")
    node = build_intent_node("triage", llm=llm)
    result = await node({"messages": [{"role": "user", "content": "谢谢你"}], "intent": "triage", "reply": "", "tool_calls": []})
    assert "复诊" in result["reply"] or "用药提醒" in result["reply"] or "症状未缓解" in result["reply"]


# ============ 图集成：情感识别不破坏路由 ============

async def test_graph_emotion_does_not_break_routing():
    """情感识别作为旁路能力不应改变意图路由（emotion 不构成意图）。"""
    from app.graph import AgentState, build_graph

    async def fake_router(_messages):
        return "general"

    graph = build_graph(router=fake_router)
    state = await graph.ainvoke(AgentState(
        messages=[{"role": "user", "content": "我是不是得了重病"}],
        intent="", reply="", tool_calls=[],
    ))
    # 焦虑情绪消息仍路由到 general（不因焦虑而改路由）
    assert state["intent"] == "general"
