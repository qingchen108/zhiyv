"""SSE 事件序列化（ADR-0014，CONTEXT §8）。

Python 是唯一事件生产者，5 种事件：delta / tool_call / card / done / error。
Java 网关字节级透传，本模块的格式即跨端协议。
"""

import json
from typing import Any

# SSE 块之间需要空行分隔，前端按 event: 名分发渲染
def sse_event(event: str, data: dict[str, Any]) -> str:
    """序列化单个 SSE 事件块。data 为 JSON 字符串（ensure_ascii=False 保证中文可读）。"""
    payload = json.dumps(data, ensure_ascii=False)
    return f"event: {event}\ndata: {payload}\n\n"


def delta_event(text: str) -> str:
    """AI 回复增量文本。"""
    return sse_event("delta", {"text": text})


def tool_call_event(tool: str, label: str) -> str:
    """工具调用轨迹（前端展示灰色提示条）。09 不接工具，11-15 使用。"""
    return sse_event("tool_call", {"tool": tool, "label": label})


def card_event(card_type: str, title: str, action: str, payload: dict[str, Any]) -> str:
    """确认卡片（action 为 Java C 端接口完整路径，payload 为 Java 草稿响应权威 JSON）。"""
    return sse_event("card", {
        "type": card_type,
        "title": title,
        "action": action,
        "payload": payload,
    })


def done_event() -> str:
    """流结束。"""
    return sse_event("done", {})


def error_event(message: str) -> str:
    """错误事件（Python 侧异常兜底；Java 转发失败走 HTTP 502，不发本事件，见 CONTEXT §8）。"""
    return sse_event("error", {"message": message})
