# 智愈（SmartMed）— 项目配置

## 语言规则

所有思考（thinking）与回复（reply）必须使用中文。

## 代码验证

所有修改完成后，必须运行完整的测试套件确认无回归。

## 参考文档

- **领域知识**：`CONTEXT.md`（技术决策上下文，术语表，全局约束）
- **架构决策**：`docs/adr/`（ADR 记录，关键设计决策）
- **Ticket 依赖**：`docs/ticket-dependencies.md`（依赖关系 DAG 图）

## 关键约束

- Python Agent 不直接访问数据库，所有数据访问经 Java API（`/api/agent/tools/*`）
- 工具契约单一来源为 `agent/tools/tools.json`
- 确认类操作（挂号/购药确认）不入 Agent 工具集，前端凭卡片 action 直调 Java