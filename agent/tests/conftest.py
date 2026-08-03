"""pytest 全局配置：在导入 app 前设置测试环境变量。

- AGENT_ECHO_MODE=true：默认走 echo 链路（无 LLM），SSE 格式测试用
- AGENT_SECRET：鉴权测试用固定密钥
- LLM_*：占位值（仅防缺失告警；echo 模式不真正调用 LLM）
"""

import os

os.environ["AGENT_ECHO_MODE"] = "true"
os.environ["AGENT_SECRET"] = "test-agent-secret"
os.environ.setdefault("LLM_API_KEY", "test-key")
os.environ.setdefault("LLM_BASE_URL", "http://localhost:9999/v1")
os.environ.setdefault("LLM_MODEL", "test-model")
