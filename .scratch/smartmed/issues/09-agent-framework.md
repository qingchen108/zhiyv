# 09 — Agent 框架搭建

**What to build:** Python Agent 服务能跑通，LangGraph 状态机骨架可接收消息并返回响应，工具定义完整，Java 侧 Agent 网关能转发对话请求并透传 SSE 流，X-Agent-Secret 鉴权生效。

**Blocked by:** 02 — 后端骨架与鉴权

**Status:** ready-for-agent

- [ ] Python 项目初始化（LangGraph + LangChain + httpx）
- [ ] LangGraph 状态机骨架：接收消息 → 意图路由 → 生成响应（先不接工具）
- [ ] LLM 接入层：适配国内模型 / AI 聚合平台（通过环境变量切换）
- [ ] 工具定义文件：声明全部 10 个工具的 name、description、parameters schema
- [ ] Python HTTP 服务：POST /agent/chat 接收消息，返回 stream 响应
- [ ] Java Agent 网关：POST /api/c/chat/stream（SSE），验 JWT + 注入 X-Patient-Id + 转发 Python
- [ ] Java 工具接口骨架：/api/agent/tools/** 路由 + X-Agent-Secret 校验过滤器
- [ ] 环境变量：AGENT_SECRET、LLM_API_KEY、LLM_BASE_URL、LLM_MODEL
- [ ] 验证：小程序发消息 → Java 转发 → Python 回复 → SSE 逐字返回（echo 模式）
