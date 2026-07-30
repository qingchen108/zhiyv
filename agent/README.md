# agent/ - AI Agent 服务

**归属 ticket**: 09 - Agent 框架搭建 起步, 后续 11/12/13/14/15 持续迭代

**技术栈**: Python + LangGraph + LangChain

**职责**: 多轮对话编排 + 工具调用 + 知识图谱推理 + 情感识别. **不直接访问任何数据库**, 所有数据访问经 Java `/api/agent/tools/*`. 详见 `CONTEXT.md` 第 5 节.

> 本目录由 01 基础设施 ticket 占位, 实际项目骨架由 09 ticket 初始化.
