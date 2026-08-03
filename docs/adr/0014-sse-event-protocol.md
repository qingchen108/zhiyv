# 0014 - SSE 事件协议与 Java 纯透传网关

对话链路（小程序 → Java → Python）采用 **Python 作为唯一事件生产者的 5 事件 SSE 协议**，Java 网关不解析不重组、字节级透传（ticket 09/10 实现）。

## 决策

- 事件集：`delta`（增量文本）/ `tool_call`（工具轨迹提示）/ `card`（确认卡片，action 为 Java C 端接口完整路径，payload 为 Java 草稿响应权威 JSON）/ `done`（流结束）/ `error`（错误）。
- Java 网关职责：验 JWT + 注入 `X-Patient-Id` + 转发 Python 响应字节流，零业务解析。
- 新增事件类型只改 Python + 前端，Java 永不感知；Java 侧转发失败（Python 未启动/超时）返回 HTTP 502，由前端兜底提示。

## Considered Options

- **Java 解析并重组事件（否决）**：Java 需感知业务语义（卡片类型、轨迹文案），每次新增事件类型都改 Java；且 Java 重写事件流引入二次序列化丢失风险。
- **前端直连 Python（否决）**：绕过 JWT 鉴权与 `X-Patient-Id` 注入，破坏身份可信链（见 CONTEXT 第 8 节）。

## Consequences

- 前端按 `event:` 名分发渲染，无需感知 Java；Java 保持无状态。
- Python 承担全部事件语义，prompt/工具迭代只动 Python。
- 协议契约记录于 CONTEXT.md 第 8 节，ticket 10 的 SSE 接收与卡片渲染直接消费本协议。
