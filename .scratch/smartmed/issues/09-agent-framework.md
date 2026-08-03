# 09 — Agent 框架搭建

**What to build:** Python Agent 服务能跑通，LangGraph 状态机骨架可接收消息并返回响应，工具定义完整，Java 侧 Agent 网关能转发对话请求并透传 SSE 流，X-Agent-Secret 鉴权生效。

**Blocked by:** 02 — 后端骨架与鉴权

**Status:** done

**定稿决策（grill-with-docs 09，详见 CONTEXT.md §5/§8 + ADR-0014/0015）：**

- SSE 事件：delta / tool_call / card / done / error 共 5 种，Python 是唯一生产者，Java 字节级透传；Java 失败返回 502
- 意图集 6 类：triage / registration / consultation / pharmacy / reminder / general（emotion 不入意图）；StateGraph 条件边骨架，真实 LLM 分类 + mock 响应
- 工具 11 个（查询 7 + 动作 4，confirm 类不入工具集，见 ADR-0015）；契约单一来源 agent/tools/tools.json；Java 泛化路由 POST /api/agent/tools/{toolName}，09 占位返回 501
- 鉴权双向：同一 AGENT_SECRET，Java 转发也带 X-Agent-Secret，Python 侧校验；X-Patient-Id 由 Java 从 JWT 注入（操作人），就诊人走 family_member_id 参数
- LLM 统一 OpenAI-compatible（ChatOpenAI + base_url/api_key/model），不做 fallback；AGENT_ECHO_MODE=true 时绕过 LLM 回显（链路 smoke）
- 请求体无状态：Body `{"messages": [{"role", "content"}]}` 全量历史；Java 首 token 60s 超时，无心跳

- [x] Python 项目初始化（FastAPI + uvicorn + LangGraph + LangChain + httpx，pip/venv/requirements.txt）
- [x] LangGraph 状态机骨架：接收消息 → 意图路由（真实 LLM 分类）→ 各意图节点 mock 响应（预留工具插槽）
- [x] LLM 接入层：ChatOpenAI + 环境变量切换，启动连通性校验；AGENT_ECHO_MODE 开关
- [x] 工具契约：agent/tools/tools.json 声明全部 11 个工具（name/description/parameters schema + Java 端点路径）
- [x] Python HTTP 服务：POST /agent/chat 收消息（校验 X-Agent-Secret），返回 5 事件 SSE 流
      （tool_call/card 序列化就绪但 09 无触发源——不接工具，11-15 接入工具后产出）
- [x] Java Agent 网关：POST /api/c/chat/stream（SSE），验 JWT + 注入 X-Patient-Id + 带 X-Agent-Secret 转发 Python，首 token 60s
- [x] Java 工具接口骨架：泛化路由 /api/agent/tools/{toolName} + X-Agent-Secret 校验过滤器 + Dispatcher 分发（handler 返回 501）
- [x] 环境变量：AGENT_SECRET（双向）、LLM_API_KEY、LLM_BASE_URL、LLM_MODEL、AGENT_ECHO_MODE
- [x] pytest 三类测试：工具契约加载 / 意图路由（mock LLM）/ SSE 输出格式（23 例全绿）
- [x] 验证：echo 模式跑通 Java→Python→SSE 链路（curl 实测 delta×2 + done，工具路由 501/401 实测）
      （“小程序→Java”一段依赖 C 端聊天页接入，归 10+ ticket；“真实 LLM 模式验证意图分类”需真实 API key，列入 11-15 验收）
