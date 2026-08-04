"""购药（pharmacy）意图节点测试（ticket 14）。

测试覆盖：
1. 辅助函数：处方 ID 提取、最近处方选取、药店对比格式化、药店选择提取
2. 意图节点行为：空消息引导、处方编号查询、查病历取最近处方、药店对比、下单草稿+卡片
3. LLM 降级：无 LLM 时走模板文案
4. 失败路径：工具不可达/库存不足
"""

import pytest

from app.pharmacy import (
    _extract_prescription_id,
    _is_recent_prescription_request,
    _pick_latest_active_prescription,
    _format_pharmacy_comparison,
    _extract_pharmacy_choice,
    build_pharmacy_node,
)


# ============ 处方 ID 提取 ============


class TestExtractPrescriptionId:
    """从用户消息中提取处方 ID。"""

    def test_digit_only(self):
        assert _extract_prescription_id("123") == 123

    def test_with_text(self):
        assert _extract_prescription_id("买处方 456 的药") == 456

    def test_no_digit(self):
        assert _extract_prescription_id("我要买药") is None

    def test_multiple_digits(self):
        assert _extract_prescription_id("处方 789 和 101") == 789  # 取第一个


class TestIsRecentPrescriptionRequest:
    """判断用户是否要求购买最近处方。"""

    def test_recent_keyword(self):
        assert _is_recent_prescription_request("买最近的药") is True

    def test_latest_keyword(self):
        assert _is_recent_prescription_request("买最新处方") is True

    def test_last_time_keyword(self):
        assert _is_recent_prescription_request("买上次的药") is True

    def test_no_keyword(self):
        assert _is_recent_prescription_request("买药") is False

    def test_with_specific_id(self):
        """含编号但无最近关键词。"""
        assert _is_recent_prescription_request("买处方 123 的药") is False


# ============ 最近 ACTIVE 处方选取 ============


class TestPickLatestActivePrescription:
    """从病历聚合中取最近 ACTIVE 处方。"""

    def test_picks_first_active(self):
        """prescriptions 已按 createdAt 降序，取第一个 ACTIVE。"""
        record = {
            "prescriptions": [
                {"id": 10, "status": "ACTIVE"},
                {"id": 9, "status": "ACTIVE"},
                {"id": 8, "status": "REVOKED"},
            ]
        }
        assert _pick_latest_active_prescription(record) == 10

    def test_skips_revoked(self):
        record = {
            "prescriptions": [
                {"id": 10, "status": "REVOKED"},
                {"id": 9, "status": "ACTIVE"},
            ]
        }
        assert _pick_latest_active_prescription(record) == 9

    def test_all_revoked(self):
        record = {
            "prescriptions": [
                {"id": 10, "status": "REVOKED"},
            ]
        }
        assert _pick_latest_active_prescription(record) is None

    def test_empty_prescriptions(self):
        assert _pick_latest_active_prescription({"prescriptions": []}) is None

    def test_missing_prescriptions_key(self):
        assert _pick_latest_active_prescription({}) is None


# ============ 药店对比格式化 ============


_PRESCRIPTION_DATA = {
    "prescription": {
        "diagnosis": "上呼吸道感染",
        "items": [
            {"drugId": 1, "drugName": "阿莫西林", "dosage": "0.5g", "frequency": "每日3次"},
            {"drugId": 2, "drugName": "布洛芬", "dosage": "0.3g", "frequency": "必要时"},
        ],
    }
}

_STOCK_BY_DRUG = {
    1: [
        {"pharmacyId": 101, "pharmacyName": "健康药店", "pharmacyAddress": "人民路1号",
         "price": "25.00", "stock": 200, "distanceM": 500, "deliveryEtaMin": 30},
        {"pharmacyId": 102, "pharmacyName": "便民药店", "pharmacyAddress": "解放路2号",
         "price": "22.00", "stock": 150, "distanceM": 1200, "deliveryEtaMin": 45},
    ],
    2: [
        {"pharmacyId": 101, "pharmacyName": "健康药店", "pharmacyAddress": "人民路1号",
         "price": "15.00", "stock": 100, "distanceM": 500, "deliveryEtaMin": 30},
        {"pharmacyId": 103, "pharmacyName": "社区药店", "pharmacyAddress": "中山路3号",
         "price": "18.00", "stock": 80, "distanceM": 800, "deliveryEtaMin": 40},
    ],
}


class TestFormatPharmacyComparison:
    """药店对比列表格式化（LLM 降级模板）。"""

    def test_lists_pharmacies(self):
        result = _format_pharmacy_comparison(_PRESCRIPTION_DATA, _STOCK_BY_DRUG)
        assert "健康药店" in result
        assert "便民药店" in result
        assert "社区药店" in result

    def test_lists_drug_count(self):
        result = _format_pharmacy_comparison(_PRESCRIPTION_DATA, _STOCK_BY_DRUG)
        assert "2 种药" in result
        assert "阿莫西林" in result
        assert "布洛芬" in result

    def test_lists_pharmacy_count(self):
        result = _format_pharmacy_comparison(_PRESCRIPTION_DATA, _STOCK_BY_DRUG)
        assert "3 家药店" in result

    def test_includes_choice_prompt(self):
        result = _format_pharmacy_comparison(_PRESCRIPTION_DATA, _STOCK_BY_DRUG)
        assert "选择" in result

    def test_empty_items(self):
        data = {"prescription": {"items": []}}
        result = _format_pharmacy_comparison(data, {})
        assert "未找到处方明细" in result

    def test_no_pharmacy_stock(self):
        result = _format_pharmacy_comparison(_PRESCRIPTION_DATA, {1: [], 2: []})
        assert "没有药店" in result


# ============ 药店选择提取 ============


_PHARMACY_OPTIONS = [
    {"pharmacyId": 101, "pharmacyName": "健康药店"},
    {"pharmacyId": 102, "pharmacyName": "便民药店"},
    {"pharmacyId": 103, "pharmacyName": "社区药店"},
]


class TestExtractPharmacyChoice:
    """从用户消息中提取药店选择。"""

    def test_number_choice(self):
        result = _extract_pharmacy_choice("1", _PHARMACY_OPTIONS)
        assert result is not None
        assert result["pharmacyId"] == 101

    def test_chinese_number_choice(self):
        result = _extract_pharmacy_choice("选第一个", _PHARMACY_OPTIONS)
        assert result is not None
        assert result["pharmacyId"] == 101

    def test_second_choice(self):
        result = _extract_pharmacy_choice("第2个", _PHARMACY_OPTIONS)
        assert result is not None
        assert result["pharmacyId"] == 102

    def test_third_choice_chinese(self):
        result = _extract_pharmacy_choice("选第三家", _PHARMACY_OPTIONS)
        assert result is not None
        assert result["pharmacyId"] == 103

    def test_invalid_choice(self):
        assert _extract_pharmacy_choice("abc", _PHARMACY_OPTIONS) is None

    def test_out_of_range(self):
        assert _extract_pharmacy_choice("5", _PHARMACY_OPTIONS) is None

    def test_zero(self):
        assert _extract_pharmacy_choice("0", _PHARMACY_OPTIONS) is None

    def test_empty_options(self):
        assert _extract_pharmacy_choice("1", []) is None


# ============ 意图节点行为测试 ============


def _make_tool_mock(monkeypatch, responses: dict):
    """构造 call_java_tool mock，按工具名返回预设响应。

    responses: {tool_name: return_value} 或 {tool_name: RuntimeError("msg")}
    多次调用同一工具返回同一值。
    """

    async def fake_call(tool_name, arguments=None):
        if tool_name not in responses:
            raise RuntimeError(f"未预期的工具调用: {tool_name}")
        val = responses[tool_name]
        if isinstance(val, Exception):
            raise val
        return val

    monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)


class TestPharmacyIntentNode:
    """购药意图节点行为测试。"""

    @pytest.mark.asyncio
    async def test_empty_messages_returns_greeting(self):
        node = build_pharmacy_node()
        result = await node({"messages": [], "intent": "pharmacy", "reply": "", "tool_calls": []})
        assert "reply" in result
        assert "处方" in result["reply"] or "购药" in result["reply"]

    @pytest.mark.asyncio
    async def test_no_id_no_recent_keyword_prompts_for_id(self, monkeypatch):
        """用户未给编号且未要求最近 -> 引导提供编号，不调工具。"""
        _make_tool_mock(monkeypatch, {})  # 无工具应被调用
        node = build_pharmacy_node()
        result = await node({
            "messages": [{"role": "user", "content": "我要买药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "编号" in result["reply"] or "处方编号" in result["reply"]

    @pytest.mark.asyncio
    async def test_prescription_id_from_message(self, monkeypatch):
        """用户给处方编号 -> 调 get_prescription + query_pharmacy_stock -> 对比文案。"""
        _make_tool_mock(monkeypatch, {
            "get_prescription": _PRESCRIPTION_DATA,
            "query_pharmacy_stock": {"stocks": _STOCK_BY_DRUG[1]},
        })
        # query_pharmacy_stock 对两个 drug_id 都返回同一列表（测试简化）
        call_log = []
        original_fake = None

        async def fake_call(tool_name, arguments=None):
            call_log.append((tool_name, arguments))
            if tool_name == "get_prescription":
                return _PRESCRIPTION_DATA
            if tool_name == "query_pharmacy_stock":
                drug_id = arguments.get("drug_id") if arguments else None
                return {"stocks": _STOCK_BY_DRUG.get(drug_id, [])}
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        result = await node({
            "messages": [{"role": "user", "content": "买处方 100 的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        # 降级模板文案含药店名
        assert "健康药店" in result["reply"]
        # 验证调用了 get_prescription + 2 次 query_pharmacy_stock
        assert ("get_prescription", {"prescription_id": 100}) in call_log
        drug_queries = [c for c in call_log if c[0] == "query_pharmacy_stock"]
        assert len(drug_queries) == 2

    @pytest.mark.asyncio
    async def test_recent_prescription_via_medical_record(self, monkeypatch):
        """用户说"买最近的药" -> 调 get_medical_record 取最近 ACTIVE 处方。"""
        record = {
            "record": {
                "prescriptions": [
                    {"id": 200, "status": "ACTIVE"},
                    {"id": 199, "status": "REVOKED"},
                ]
            }
        }

        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_medical_record":
                return record
            if tool_name == "get_prescription":
                return _PRESCRIPTION_DATA
            if tool_name == "query_pharmacy_stock":
                drug_id = arguments.get("drug_id") if arguments else None
                return {"stocks": _STOCK_BY_DRUG.get(drug_id, [])}
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        result = await node({
            "messages": [{"role": "user", "content": "买最近的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "健康药店" in result["reply"]
        # tool_calls 应含 get_medical_record
        tool_names = [tc.get("tool") for tc in result.get("tool_calls", [])]
        assert "get_medical_record" in tool_names

    @pytest.mark.asyncio
    async def test_no_active_prescription(self, monkeypatch):
        """病历中无 ACTIVE 处方 -> 提示无可用处方。"""
        record = {"record": {"prescriptions": [{"id": 200, "status": "REVOKED"}]}}

        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_medical_record":
                return record
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        result = await node({
            "messages": [{"role": "user", "content": "买最近的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "处方" in result["reply"]

    @pytest.mark.asyncio
    async def test_create_order_draft_with_card(self, monkeypatch):
        """用户选药店 -> 调 create_order_draft -> 返回 order_confirm 卡片。"""
        # 先走对比阶段
        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_prescription":
                return _PRESCRIPTION_DATA
            if tool_name == "query_pharmacy_stock":
                drug_id = arguments.get("drug_id") if arguments else None
                return {"stocks": _STOCK_BY_DRUG.get(drug_id, [])}
            if tool_name == "create_order_draft":
                return {
                    "draftKey": "order_draft:1:100",
                    "confirmToken": "abc123",
                    "prescriptionId": 100,
                    "pharmacyId": 101,
                    "pharmacyName": "健康药店",
                    "totalAmount": "40.00",
                    "items": [{"drugId": 1, "dosage": "0.5g", "frequency": "每日3次"}],
                }
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        # 第一轮：对比阶段
        await node({
            "messages": [{"role": "user", "content": "买处方 100 的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        # 第二轮：用户选药店 1
        result = await node({
            "messages": [{"role": "user", "content": "1"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "card" in result
        assert result["card"] is not None
        # card 是 SSE 字符串，含 order_confirm
        assert "order_confirm" in result["card"]
        assert "健康药店" in result["reply"]
        assert "用药提醒" in result["reply"]

    @pytest.mark.asyncio
    async def test_create_order_draft_failure(self, monkeypatch):
        """create_order_draft 失败 -> 错误提示。"""
        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_prescription":
                return _PRESCRIPTION_DATA
            if tool_name == "query_pharmacy_stock":
                drug_id = arguments.get("drug_id") if arguments else None
                return {"stocks": _STOCK_BY_DRUG.get(drug_id, [])}
            if tool_name == "create_order_draft":
                raise RuntimeError("药品库存不足")
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        # 第一轮：对比阶段
        await node({
            "messages": [{"role": "user", "content": "买处方 100 的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        # 第二轮：用户选药店 1，但 create_order_draft 失败
        result = await node({
            "messages": [{"role": "user", "content": "1"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "失败" in result["reply"] or "库存" in result["reply"]
        # 失败不应产生卡片
        assert result.get("card") is None

    @pytest.mark.asyncio
    async def test_invalid_pharmacy_choice_reprompts(self, monkeypatch):
        """awaiting_choice 阶段用户输入无效选择 -> 重新提示。"""
        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_prescription":
                return _PRESCRIPTION_DATA
            if tool_name == "query_pharmacy_stock":
                drug_id = arguments.get("drug_id") if arguments else None
                return {"stocks": _STOCK_BY_DRUG.get(drug_id, [])}
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        # 第一轮：对比阶段
        await node({
            "messages": [{"role": "user", "content": "买处方 100 的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        # 第二轮：无效选择
        result = await node({
            "messages": [{"role": "user", "content": "abc"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "选择" in result["reply"]

    @pytest.mark.asyncio
    async def test_llm_unavailable_uses_template(self, monkeypatch):
        """无 LLM（echo 模式）-> 走模板文案，不报错。"""
        _make_tool_mock(monkeypatch, {})  # 占位，下面覆盖

        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_prescription":
                return _PRESCRIPTION_DATA
            if tool_name == "query_pharmacy_stock":
                drug_id = arguments.get("drug_id") if arguments else None
                return {"stocks": _STOCK_BY_DRUG.get(drug_id, [])}
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        # llm=None 模拟 echo 模式
        node = build_pharmacy_node(llm=None)
        result = await node({
            "messages": [{"role": "user", "content": "买处方 100 的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        # 模板文案含药店名和选择提示
        assert "健康药店" in result["reply"]
        assert "选择" in result["reply"]

    @pytest.mark.asyncio
    async def test_prescription_query_failure(self, monkeypatch):
        """get_prescription 失败 -> 错误提示。"""
        async def fake_call(tool_name, arguments=None):
            if tool_name == "get_prescription":
                raise RuntimeError("Java 不可达")
            raise RuntimeError(f"未预期: {tool_name}")

        monkeypatch.setattr("app.pharmacy.call_java_tool", fake_call)

        node = build_pharmacy_node()
        result = await node({
            "messages": [{"role": "user", "content": "买处方 100 的药"}],
            "intent": "pharmacy", "reply": "", "tool_calls": [],
        })
        assert "reply" in result
        assert "失败" in result["reply"]
