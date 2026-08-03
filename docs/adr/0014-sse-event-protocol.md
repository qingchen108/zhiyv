# 0014 - 对话事件协议与 Java 网关（修订：SSE → WebSocket 容器）

对话链路（小程序 → Java → Python）采用 **Python 作为唯一事件生产者的 5 事件协议**，Java 网关只做传输格式转换、不解析业务语义（ticket 09 定稿，10 修订）。

## 决策

- 事件集：`delta`（增量文本）/ `tool_call`（工具轨迹提示）/ `card`（确认卡片，action 为 Java C 端接口完整路径，payload 为 Java 草稿响应权威 JSON）/ `done`（流结束）/ `error`（错误）。
- 事件生产者：仅 Python，事件语义与 JSON 结构跨端唯一事实来源（`agent/app/sse.py`）。
- 传输容器（2026-08-03 修订）：Java → Python 仍为 HTTP SSE；**小程序侧改 WebSocket 短连接**（`/api/c/chat/ws`，每次发送建一条 WS，done 后关闭）。原因：支付宝小程序 `my.request` 无分块响应能力（无 enableChunked/onChunkReceived），无法原生消费 SSE。WS 帧为 JSON `{"event": "...", "data": {...}}`，字段与 SSE 5 事件完全一致，前端按 event 分发，渲染逻辑与传输容器解耦。
- Java 网关职责（修订）：验 JWT（握手拦截器，header `Authorization: Bearer`）+ 注入 `X-Patient-Id` + 消费 Python SSE 流逐块转 WS 帧。**Java 仍不解析事件业务语义**，仅按 SSE 块切分转发；新增事件类型仍只改 Python + 前端。
- Java 侧失败（Python 未启动/超时）：WS 直接关闭（close code 1011），**Java 不发 error 帧**（error 为 Python 专属事件）；前端 onClose 按 code 显示兜底文案（对应修订前 SSE 语义的 HTTP 502）。

## Considered Options

- **小程序直连 Python（否决）**：绕过 JWT 鉴权与 `X-Patient-Id` 注入，破坏身份可信链（见 CONTEXT 第 8 节）。
- **Java 解析并重组事件（否决）**：Java 需感知业务语义（卡片类型、轨迹文案），每次新增事件类型都改 Java；且重写事件流引入二次序列化丢失风险。
- **非流式降级 / 分片轮询（否决）**：丧失真流式体验（PRD C03 逐字输出），或引入轮询游标复杂度。
- **长连接 WebSocket（否决）**：需 requestId 关联、心跳保活、断线重连状态机；支付宝小程序同时仅允许一条 WS 连接。短连接与 SSE 请求-响应语义一致，失败边界清晰。

## Consequences

- 前端按 `event` 字段分发渲染，不感知传输容器；Java 保持无状态。
- Python 承担全部事件语义，prompt/工具迭代只动 Python。
- Java 网关从"字节级透传"变为"传输格式转换"，仍不解析业务语义——协议纯净性保留在"事件语义"层面。
- 旧 `/api/c/chat/stream`（SSE 端点）可保留给调试/B 端扩展，C 端小程序仅走 WS。
- 协议契约记录于 CONTEXT.md 第 8 节，ticket 10 的 WS 接收、卡片渲染、轨迹展示直接消费本协议。
