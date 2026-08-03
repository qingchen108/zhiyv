# agent/ - AI Agent 服务

**归属 ticket**: 09 - Agent 框架搭建 起步, 后续 11/12/13/14/15 持续迭代

**技术栈**: Python + FastAPI + LangGraph + LangChain + httpx

**职责**: 多轮对话编排 + 工具调用 + 知识图谱推理 + 情感识别. **不直接访问任何数据库**, 所有数据访问经 Java `/api/agent/tools/*`. 详见 `CONTEXT.md` 第 5 节.

## 快速开始

```bash
# 1. 依赖 (Windows / Linux 均可)
python -m venv .venv
# Windows: .venv\Scripts\activate ; Linux: source .venv/bin/activate
pip install -r requirements-dev.txt

# 2. 配置
cp .env.example .env   # 填 LLM_BASE_URL/LLM_API_KEY/LLM_MODEL/AGENT_SECRET

# 3. 启动 (echo 模式可先链路 smoke; 真实模式需 LLM 配置)
uvicorn app.main:app --port 8000
# AGENT_ECHO_MODE=true 时绕过 LLM 回显, 无需 API key

# 4. 测试
pytest
```

## 接口

| 接口 | 说明 |
|------|------|
| `POST /agent/chat` | 对话入口, 需 `X-Agent-Secret`, 返回 SSE 事件流 (ADR-0014) |
| `GET /health` | 健康检查 |

## 目录结构

- `app/main.py` - FastAPI 入口: /agent/chat + SSE 流
- `app/graph.py` - LangGraph 状态机骨架 (router → 意图节点)
- `app/intents.py` - 意图集 (6 类) + LLM 意图路由 + mock 意图节点
- `app/llm.py` - 统一 Anthropic Messages API 接入 (三变量切换, 火山方舟 coding 端点) + 启动连通性校验
- `app/sse.py` - SSE 事件序列化 (5 事件协议)
- `app/security.py` - X-Agent-Secret 双向鉴权
- `tools/tools.json` - 11 个工具契约**单一来源** (Java 侧按此实现, 见 CONTEXT §5)
- `tests/` - pytest: 工具契约 / 意图路由 / SSE 格式
