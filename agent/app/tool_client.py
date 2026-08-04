"""Java 工具调用 HTTP 客户端（ticket 11）。

从 tools.json 加载工具契约，通过 HTTP 调用 Java 的 /api/agent/tools/{name} 端点。
使用 httpx 异步客户端，带 X-Agent-Secret 鉴权。
"""

import json
import logging
from pathlib import Path
from typing import Any

import httpx

from app.config import get_settings

logger = logging.getLogger(__name__)

# tools.json 路径（相对于 agent/ 目录）
_TOOLS_JSON = Path(__file__).resolve().parents[1] / "tools" / "tools.json"


def load_tools() -> list[dict[str, Any]]:
    """加载 tools.json 契约，返回工具列表。"""
    data = json.loads(_TOOLS_JSON.read_text(encoding="utf-8"))
    return data["tools"]


def get_tool_endpoint(tool_name: str) -> str | None:
    """按工具名查 java_endpoint。"""
    for t in load_tools():
        if t["name"] == tool_name:
            return t["java_endpoint"]
    return None


async def call_java_tool(tool_name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
    """调用 Java 工具端点，返回反序列化 JSON。

    Args:
        tool_name: 工具名（蛇形，与 tools.json 一致）
        arguments: 工具参数（可选）

    Returns:
        Java 统一响应体 Result 的 data 字段

    Raises:
        RuntimeError: 工具不存在、Java 不可达、工具返回错误码
    """
    settings = get_settings()
    endpoint = get_tool_endpoint(tool_name)
    if not endpoint:
        raise RuntimeError(f"未知工具: {tool_name}")

    url = settings.java_gateway_url.rstrip("/") + endpoint
    body = {"arguments": arguments or {}}

    async with httpx.AsyncClient(timeout=30) as client:
        try:
            resp = await client.post(
                url,
                json=body,
                headers={"X-Agent-Secret": settings.agent_secret},
            )
        except httpx.RequestError as e:
            logger.error("Java 工具调用失败: tool=%s url=%s error=%s", tool_name, url, e)
            raise RuntimeError(f"工具 {tool_name} 不可用：Java 服务不可达") from e

    if resp.status_code != 200:
        logger.error("Java 工具返回非 200: tool=%s status=%s body=%s", tool_name, resp.status_code, resp.text)
        raise RuntimeError(f"工具 {tool_name} 调用失败：HTTP {resp.status_code}")

    result = resp.json()
    code = result.get("code")
    if code != 200:
        msg = result.get("message", "未知错误")
        logger.error("Java 工具返回错误: tool=%s code=%s message=%s", tool_name, code, msg)
        raise RuntimeError(f"工具 {tool_name} 返回错误：{msg}")

    return result.get("data") or {}