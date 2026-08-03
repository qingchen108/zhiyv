# 0016 - Agent 工具 handler 复用既有 Service 层

Agent 工具（`/api/agent/tools/{toolName}`）的实现**直接委托给既有业务 Service**，不为 Agent 路径新建第二套 Service。ticket 12 的 `create_registration_draft` 委托 `RegistrationService.createDraft()`，ticket 14 的 `create_order_draft` 委托新建的 `OrderService.createDraft()`。

## 决策

- Agent handler 与 C 端 Controller 共享同一 Service 层，区别仅在身份获取方式：C 端从 JWT 取 `patientId`，Agent 从 `X-Patient-Id` header 取（AgentToolFilter 已注入）。
- `query_schedule` 返回扁平列表（`schedule_id` / `doctor_id` / `doctor_name` / `department_name` / `schedule_date` / `time_period` / `remaining_slots` / `status`），过滤 SUSPENDED 和余量为 0。
- `query_pharmacy_stock` 保持单 `drug_id` 查询（不改 tools.json 契约），多药处方由 Agent 多次调用后 LLM 侧聚合对比。
- 不新增 `query_family_members` 工具，Agent 通过 `get_medical_record` 返回的家庭成员列表识别就诊人。
- `create_reminder` 工具降级为手动补设/修改用途，购药提醒主要由 `/api/c/orders/confirm` 同事务自动创建。

## Considered Options

- **Agent 独立 Service 层（否决）**：挂号/购药草稿逻辑重复维护，两端 Service 容易漂移；Redis key 格式、防刷逻辑、校验规则需同步两份代码。
- **批量 `query_pharmacy_stock` 参数 `drug_ids: [int]`（否决）**：改 tools.json 契约需 Python + Java 双端同步，种子数据仅 3 家药店 × 7 种药，LLM 多次调用聚合完全可行。
- **新增 `query_family_members` 工具（否决）**：`get_medical_record` 已含家庭成员信息，新增工具增加契约维护成本，违反工具最小集原则。

## Consequences

- AgentToolDispatcher 从 501 桩实现改为按 toolName 注入对应 Service handler，dispatcher 代码保持薄分发层。
- Agent 工具返回格式由各 Service 的 VO/DTO 决定，不额外封装 Agent 专属 DTO。
- ticket 12/14 的实现工作量大幅缩减：Java 侧只需写 handler → Service 委托，不重复业务逻辑。
