# 14 — Agent 购药与用药提醒

**What to build:** 处方解读无风险后，Agent 推荐附近药店（价格/距离/时效对比），患者选择药店后生成购药确认卡片，确认后创建订单（含库存扣减），自动按处方频次设置用药提醒。

**Blocked by:** 13 — Agent 预问诊与处方解读

**Status:** in-progress（后端+Agent 完成，前端购药凭证渲染待补）

## 决策（grill-with-docs 2026-08-03）

- `query_pharmacy_stock` 保持单 drug_id 查询（不改 tools.json），多药处方 Agent 多次调用后 LLM 侧聚合对比（ADR-0016）
- 新建 `POST /api/c/orders/confirm`，Body `{ draftKey, confirmToken, prescriptionId, pharmacyId }`（ADR-0017）
- 购药草稿 key：`order_draft:{patientId}:{prescriptionId}`，TTL 30min
- 购药确认卡片 `type=order_confirm`，`action=/api/c/orders/confirm`，payload 含 draftKey / confirmToken / prescriptionId / pharmacyId / pharmacyName / items / totalAmount
- confirm 同事务扣减 `drug_pharmacy_stock.stock`（SQL），stock 不足 → 400；不引入 Redis 并发控制
- 种子数据调大库存量（如 50→200），确保演示多次下单不耗尽
- 用药提醒由 confirm 同事务自动创建（遍历处方明细按 frequency 关键词匹配），`create_reminder` 工具降级为手动补设
- frequency 解析：简单关键词匹配（"1 次"→08:00 / "2 次"→08:00,18:00 / "3 次"→08:00,12:00,18:00 / 无法匹配→次日 08:00 + frequency 原文保留）
- 购药成功凭证卡片前端本地渲染（confirm API 返回 DrugOrderVO），不进 SSE 事件流

## 验收标准

- [x] Java 工具实现：query_pharmacy_stock（按 drug_id 查各药店库存/价格/时效，返回扁平列表）
- [x] Java 工具实现：create_order_draft（写 Redis 草稿 TTL 30min + SHA-256 confirmToken）
- [x] Java：`POST /api/c/orders/confirm`（消费草稿 -> 扣库存 -> 写 drug_order -> 自动生成 medication_reminder -> 返回 DrugOrderVO）
- [x] Agent 展示药店对比（3 家药店，含距离、价格、配送时间）-> 用户选择 -> 生成购药确认卡片
- [x] 购药确认卡片：type=order_confirm + payload 含完整草稿信息
- [ ] 用户确认 -> 前端直调 /api/c/orders/confirm -> 成功前端本地渲染订单卡片（前端未实现 order_confirm 专属渲染与 DrugOrderVO 凭证卡片，当前复用挂号通用模板，按钮文案/提示为挂号语义）
- [ ] 库存不足 -> confirm 返回 400 -> 前端 toast 提示（后端已返回 400 + "药品库存不足"，前端仅有通用失败兜底显示在卡片内，无 toast 且无库存专属处理）
- [x] 购药成功 -> 自动生成用药提醒（frequency 关键词匹配 -> 提醒时间点）
- [x] Agent 购药成功文案提示"已为您设置用药提醒"
- [x] 种子数据 drug_pharmacy_stock 库存调大（50->200）
- [x] pytest 测试：药店推荐、下单流程、提醒生成逻辑、库存扣减
