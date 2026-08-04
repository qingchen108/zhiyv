"""预问诊与处方解读（consultation）意图节点测试（ticket 13）。

测试覆盖：
1. 格式化函数：预问诊摘要格式化、处方格式化、过敏警告格式化
2. 辅助函数：处方 ID 提取、药物名称提取
3. 意图节点基础行为：空消息、场景路由
"""

import pytest

from app.consultation import (
    _format_pre_diagnosis_summary,
    _format_prescription_for_llm,
    _format_allergy_warning,
    _extract_prescription_id,
    _extract_drug_names,
    _extract_json_from_llm,
)
from app.intents import build_intent_node


# ============ 预问诊摘要格式化 ============


class TestFormatPreDiagnosisSummary:
    """预问诊摘要格式化。"""

    def test_full_summary(self):
        data = {
            "chief_complaint": "头痛3天",
            "present_history": "3天前开始头痛，呈持续性钝痛",
            "past_history": "高血压病史5年",
            "allergy_history": "青霉素过敏",
        }
        result = _format_pre_diagnosis_summary(data)
        assert "主诉" in result
        assert "头痛3天" in result
        assert "现病史" in result
        assert "既往史" in result
        assert "过敏史" in result
        assert "AI 生成" in result

    def test_partial_summary(self):
        """仅部分字段有值。"""
        data = {
            "chief_complaint": "发烧2天",
            "present_history": "",
            "past_history": "",
            "allergy_history": "",
        }
        result = _format_pre_diagnosis_summary(data)
        assert "主诉" in result
        assert "发烧2天" in result
        assert "现病史" not in result  # 空字段不显示

    def test_empty_summary(self):
        """所有字段为空。"""
        data = {"chief_complaint": "", "present_history": "", "past_history": "", "allergy_history": ""}
        result = _format_pre_diagnosis_summary(data)
        assert "未提供详细信息" in result


# ============ 处方格式化 ============


class TestFormatPrescriptionForLLM:
    """处方数据格式化。"""

    def test_normal_prescription(self):
        data = {
            "prescription": {
                "diagnosis": "上呼吸道感染",
                "advice": "多喝水",
                "items": [
                    {"drugName": "阿莫西林", "usageMethod": "口服", "dosage": "0.5g", "frequency": "每日3次", "remark": "饭后"},
                    {"drugName": "布洛芬", "usageMethod": "口服", "dosage": "0.3g", "frequency": "必要时", "remark": ""},
                ],
            }
        }
        result = _format_prescription_for_llm(data)
        assert "阿莫西林" in result
        assert "布洛芬" in result
        assert "诊断" in result
        assert "多喝水" in result
        assert "口服" in result
        assert "0.5g" in result

    def test_empty_items(self):
        data = {"prescription": {"diagnosis": "", "advice": "", "items": []}}
        result = _format_prescription_for_llm(data)
        assert "未找到处方明细" in result


# ============ 过敏警告格式化 ============


class TestFormatAllergyWarning:
    """过敏警告格式化。"""

    def test_allergy_warning(self):
        warnings = [
            {"type": "ALLERGY", "drugName": "阿莫西林", "targetName": "青霉素", "description": "药物含青霉素过敏原"},
        ]
        result = _format_allergy_warning(warnings)
        assert "过敏风险警告" in result
        assert "阿莫西林" in result
        assert "青霉素" in result
        assert "联系医生" in result

    def test_interaction_warning(self):
        warnings = [
            {"type": "INTERACTION", "drugName": "阿司匹林", "targetName": "华法林", "description": "相互作用"},
        ]
        result = _format_allergy_warning(warnings)
        assert "相互作用" in result
        assert "阿司匹林" in result
        assert "华法林" in result

    def test_mixed_warnings(self):
        warnings = [
            {"type": "ALLERGY", "drugName": "阿莫西林", "targetName": "青霉素", "description": "过敏"},
            {"type": "INTERACTION", "drugName": "布洛芬", "targetName": "阿司匹林", "description": "相互作用"},
        ]
        result = _format_allergy_warning(warnings)
        assert "阿莫西林" in result
        assert "布洛芬" in result
        assert "联系医生" in result

    def test_empty_warnings(self):
        result = _format_allergy_warning([])
        assert result == ""


# ============ 处方 ID 提取 ============


class TestExtractPrescriptionId:
    """从用户消息中提取处方 ID。"""

    def test_digit_only(self):
        assert _extract_prescription_id("123") == 123

    def test_with_text(self):
        assert _extract_prescription_id("我的处方编号是 456") == 456

    def test_no_digit(self):
        assert _extract_prescription_id("我要看处方") is None

    def test_multiple_digits(self):
        assert _extract_prescription_id("处方 789 和 101") == 789  # 取第一个


# ============ 药物名称提取 ============


class TestExtractDrugNames:
    """从用户消息中提取药物名称。"""

    def test_single_drug(self):
        result = _extract_drug_names("检查阿莫西林是否过敏")
        assert "阿莫西林" in result

    def test_multiple_drugs(self):
        result = _extract_drug_names("查一下阿莫西林和布洛芬")
        assert "阿莫西林" in result
        assert "布洛芬" in result

    def test_no_drug(self):
        result = _extract_drug_names("帮我查一下过敏")
        assert result == []

    def test_partial_match(self):
        """头孢匹配头孢克肟、头孢拉定等。"""
        result = _extract_drug_names("检查头孢")
        assert "头孢" in result


# ============ 意图节点基础行为 ============


class TestConsultationIntentNode:
    """预问诊意图节点基础行为。"""

    @pytest.mark.asyncio
    async def test_empty_messages_returns_greeting(self):
        node = build_intent_node("consultation")
        result = await node({"messages": [], "intent": "consultation", "reply": "", "tool_calls": []})
        assert "reply" in result
        assert "健康助手" in result["reply"]
        assert "预问诊" in result["reply"]
        assert "处方解读" in result["reply"]

    @pytest.mark.asyncio
    async def test_consultation_node_returns_dict(self):
        node = build_intent_node("consultation")
        result = await node({
            "messages": [{"role": "user", "content": "我想看处方"}],
            "intent": "consultation", "reply": "", "tool_calls": [],
        })
        assert "reply" in result

    @pytest.mark.asyncio
    async def test_prescription_detection(self):
        """检测到处方关键词。"""
        node = build_intent_node("consultation")
        result = await node({
            "messages": [{"role": "user", "content": "我要查处方"}],
            "intent": "consultation", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        # 处方未提供 ID，应提示提供编号
        assert "编号" in result["reply"] or "处方编号" in result["reply"]

    @pytest.mark.asyncio
    async def test_allergy_detection(self):
        """检测到过敏关键词。"""
        node = build_intent_node("consultation")
        result = await node({
            "messages": [{"role": "user", "content": "帮我检查过敏"}],
            "intent": "consultation", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        # 未提供药物名称，应提示提供
        assert "药物名称" in result["reply"] or "药" in result["reply"]

    @pytest.mark.asyncio
    async def test_pre_consult_default(self):
        """默认走预问诊流程。"""
        node = build_intent_node("consultation")
        result = await node({
            "messages": [{"role": "user", "content": "我头痛3天了"}],
            "intent": "consultation", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        # 在 echo 模式下，LLM 不可用，走 mock 分支
        assert "预问诊" in result["reply"] or "症状" in result["reply"] or "病情" in result["reply"]
