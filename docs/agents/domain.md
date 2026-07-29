# Domain Docs — Single Context

## 布局

- `CONTEXT.md`（根目录）：项目技术决策 + 术语表，所有 skill 的参考来源
- `docs/adr/`：架构决策记录（ADR），按 `NNN-title.md` 命名

## 消费规则

- 任何 skill 在修改代码前应读取 `CONTEXT.md` 了解已确认的技术决策
- 新的架构决策应记录为 ADR 并更新 `CONTEXT.md` 术语表
- 术语表中的定义是团队共识，不可在代码中偏离
