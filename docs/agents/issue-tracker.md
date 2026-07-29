# Issue Tracker — Local Markdown

## 位置

Issues 存放在 `.scratch/<feature>/issues/` 目录下，每个 issue 一个 markdown 文件。

## 文件命名

`<number>-<short-slug>.md`，如 `001-login-flow.md`

## Issue 文件格式

```markdown
# <标题>

**Status**: needs-triage | needs-info | ready-for-agent | ready-for-human | wontfix
**Labels**: [逗号分隔]
**Blocking**: [依赖的 issue 编号]

## 描述

...

## 验收标准

- [ ] ...
```

## 工作流

1. 新建 issue 时状态设为 `needs-triage`
2. 分类后改为 `ready-for-agent` 或 `ready-for-human`
3. 完成后删除或归档到 `.scratch/<feature>/done/`
