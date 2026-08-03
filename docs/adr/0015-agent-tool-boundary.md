# 0015 - Agent 工具边界：只建草稿，不替用户确认

Agent 工具集**不包含任何确认类工具**（挂号确认 / 下单确认），Agent 只能创建草稿，用户通过 SSE `card` 事件手动确认，前端凭卡片 `action` 直调 Java C 端接口完成写操作（ticket 09 定义，12/14 实现）。

## 决策

- 11 个工具 = 查询类 7 + 动作类 4（`create_registration_draft` / `write_pre_diagnosis` / `create_order_draft` / `create_reminder`），无 `confirm_registration` / `confirm_order`。
- 确认链路数据源为 **Java 草稿响应权威 JSON**，Python 原样放入卡片 `payload`，LLM 在确认链路中零参与（其记忆不可信：多轮对话中可能记错日期/班次/就诊人）。
- 挂号/购药复用 C 端既有两段式草稿机制（`reg_draft` / `order_draft`，TTL 30min），Agent 不绕过它。

## Considered Options

- **Agent 直接调 confirm（否决）**：LLM 幻觉可导致错误挂号（占用号源）/错误订单（脏数据），医疗场景后果真实、责任归属模糊；参数经 LLM 记忆传递无防错闸门。
- **Agent 复述确认 + LLM 转述用户授权（否决）**：仍把参数安全寄托于 LLM 记忆；卡片方案中用户确认的与 Java 生成的是同一份数据，零 LLM 失真。
- **confirm 类工具仅由前端直调 Java（采纳）**：缩小 Agent 写操作攻击面，prompt 注入最多能创建草稿，无法完成任何下单。

## Consequences

- 交互多一次点击（卡片确认），这是防错闸门而非摩擦。
- 动作类工具的 LLM 输出即"卡片渲染请求"：工具调用结果直接映射 `card` 事件，Java 端无对应 `confirm_*` 工具端点。
- 卡片 `action` 完整路径使前端零映射；ticket 12/14 实现草稿工具时同步产出卡片。
