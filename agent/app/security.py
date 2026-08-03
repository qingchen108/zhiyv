"""X-Agent-Secret 双向鉴权（CONTEXT §5）。

Python 侧校验 FastAPI 依赖：Java 转发 /agent/chat 时必须携带与 Java 相同的密钥。
使用 secrets.compare_digest 常量时间比较，避免时序侧信道。
"""

import secrets

from fastapi import Header, HTTPException

from app.config import get_settings

# 校验失败的统一提示（不区分"缺头"与"值错"，避免枚举探测）
_INVALID_SECRET_MSG = "X-Agent-Secret 校验失败"


def require_agent_secret(x_agent_secret: str = Header(default="")) -> None:
    """FastAPI 依赖：校验 X-Agent-Secret 请求头与配置的 AGENT_SECRET 一致。"""
    settings = get_settings()
    if not secrets.compare_digest(x_agent_secret, settings.agent_secret):
        raise HTTPException(status_code=401, detail=_INVALID_SECRET_MSG)
