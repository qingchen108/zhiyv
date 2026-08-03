# 0017 - 购药确认接口、库存扣减与用药提醒自动生成

ticket 14 新增 C 端购药确认接口 `POST /api/c/orders/confirm`，与挂号两段式对齐（草稿→确认），confirm 同事务内扣减药店库存并自动生成用药提醒。

## 决策

### 购药确认接口

- `POST /api/c/orders/confirm`，Body `{ draftKey, confirmToken, prescriptionId, pharmacyId }`。
- 消费草稿（DEL Redis key）→ 查 drug_pharmacy_stock 计算总价 → 写 `drug_order` 表 → 同事务生成 `medication_reminder` → 返回 `DrugOrderVO`。
- 草稿 key 格式：`order_draft:{patientId}:{prescriptionId}`，TTL 30min，与挂号草稿对齐。

### 库存扣减

- confirm 同事务 `UPDATE drug_pharmacy_stock SET stock = stock - quantity WHERE drug_id = ? AND pharmacy_id = ? AND stock >= quantity`。
- stock 不足 → 400 拒绝（"该药店库存不足"）。
- 不引入 Redis 并发控制（演示场景无并发购药压力，与号源 Lua 脚本不同）。
- 种子数据调大库存量（如 50→200），确保演示多次下单不耗尽。

### 用药提醒自动生成

- confirm 成功后同事务遍历处方明细（prescription_item），按每种药的 `frequency` 生成 `medication_reminder` 记录。
- frequency 解析用简单关键词匹配：
  - "1 次" → 08:00
  - "2 次" → 08:00, 18:00
  - "3 次" → 08:00, 12:00, 18:00
  - 无法匹配 → 仅创建一条 `nextRemindAt = 次日 08:00`，frequency 原文保留
- `next_remind_at` 从当前时间推算最近的提醒点。
- 不做定时调度推送（Out of Scope），仅写记录供前端列表展示。

### 确认卡片与凭证

- 购药确认卡片：`type=order_confirm`，`action=/api/c/orders/confirm`，payload 含 `draftKey` / `confirmToken` / `prescriptionId` / `pharmacyId` / `pharmacyName` / `items` / `totalAmount`。
- 购药成功凭证卡片：前端本地渲染（confirm API 返回 `DrugOrderVO`），不进 SSE 事件流，与挂号凭证渲染方式一致。
- Agent 在下一轮对话中说"已为您设置用药提醒"（纯文案，不调工具）。

## Considered Options

- **不扣库存（否决）**：stock 字段存在却不使用违反数据完整性直觉，演示时多次下单无库存变化显得不真实；改大种子数据 + 简单 SQL 扣减即可。
- **Agent 逐个调 `create_reminder` 工具（否决）**：处方含 N 种药 = N 次工具调用，增加延迟和 LLM 出错概率；提醒是确定性副产物不需要 LLM 判断。
- **frequency NLP 解析（否决）**：引入 NLP 库过度设计，种子数据频次文本可控（"每日 2 次"等），简单关键词匹配足够。
- **Redis 并发控制库存扣减（否决）**：购药无抢单场景，演示环境无并发压力，SQL 事务足够。

## Consequences

- 新增 `OrderService`（购药草稿 + 确认逻辑），与 `RegistrationService` 两段式模式对齐。
- `create_reminder` Agent 工具保留但降级为手动补设用途，主要调用路径来自 confirm 内部。
- ticket 14 实现范围明确：Java 侧 `OrderController` + `OrderService` + Agent handler 委托；Agent 侧 pharmacy 意图节点 prompt 模板。
