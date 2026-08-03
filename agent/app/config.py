"""SmartMed Agent 服务配置（09 ticket，CONTEXT §5）。

环境变量以 agent/.env.example 为准，pydantic-settings 读取 agent/.env（从 agent/ 目录启动）。
LLM 统一 OpenAI-compatible 接口：仅 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL 三变量切换，不引入各家 SDK。
"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ---------- LLM（统一 OpenAI-compatible） ----------
    llm_api_key: str = ""
    llm_base_url: str = ""
    llm_model: str = ""

    # ---------- Java 网关 ----------
    # Agent 调工具用（09 不接工具，预留；Java 默认监听 8080）
    java_gateway_url: str = "http://localhost:8080"

    # ---------- Agent 鉴权（双向同一密钥，见 CONTEXT §5） ----------
    agent_secret: str = "smartmed-dev-agent-secret-change-me"

    # ---------- 验证开关（CONTEXT §8） ----------
    # true: 绕过 LLM 直接回显最后一条用户消息（链路 smoke，无 API key 可跑）
    # false: 真实 LLM 意图分类 + mock 意图节点回复
    agent_echo_mode: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()
