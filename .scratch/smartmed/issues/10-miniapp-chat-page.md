# 10 — C 端 AI 对话页

**What to build:** 患者在小程序对话页输入文字后，看到 AI 逐字流式回复，对话中嵌入确认卡片（挂号/购药）可点击操作，能看到工具调用轨迹（"正在查询知识图谱..."），整体交互流畅。

**Blocked by:** 07 — C 端小程序基础, 09 — Agent 框架搭建

**Status:** done

**定稿决策（grill-with-docs 10，详见 CONTEXT.md §3/§8 + ADR-0014 修订）：**

- 传输容器：小程序侧 WebSocket 短连接 `/api/c/chat/ws`（每次发送建一条 WS，done 后关闭；握手 header 带 JWT）；Java 内部消费 Python SSE 流逐块转 WS 帧 `{"event","data"}`，事件语义与 5 事件协议（delta/tool_call/card/done/error）完全一致；Python 零改动
- 失败语义：error 事件为 Python 专属；Java 转发失败（Python 未启动/超时）→ WS close 1011，前端 onClose 显示兜底文案；30s 无事件提示断开
- 存储责任：前端保存（Java 网关零业务解析）；首条消息时创建会话（title=首条消息截断 ≤20 字）；done 后每轮一次批量原子保存（user + tool×N + assistant）；失败轮次不落库
- 轨迹与卡片：tool_call 累积为 TOOL 消息落库（tool_trace JSONB）；card 事件不落库不重渲染（草稿 30min 过期）
- 会话管理：对话页顶栏弹层（标题+更新时间，updated_at 倒序，每项可删除）；历史加载后端为准，本地缓存（storage key=sessionId）仅同页切换兜底
- 交互：串行发送（流式期间禁用输入）；失败气泡 + 重试按钮（原样重发该轮）
- 后端 API：POST/GET `/api/c/chat/sessions`、GET/POST `/api/c/chat/sessions/{id}/messages`、DELETE `/api/c/chat/sessions/{id}`（物理删除级联消息）；归属校验 patient_id=JWT，非本人 404

- [x] 后端：会话/消息 CRUD API（chat_session / chat_message 表已建，归属校验 + 批量追加 + 级联删除）
- [x] 后端：WebSocket 网关端点 `/api/c/chat/ws`（握手拦 JWT → 消费 Python SSE → 逐块转 WS 帧 → done 后关闭；转发失败 close 1011）
- [x] 对话页 UI：气泡布局（AI 左侧 / 用户右侧，流式期间 AI 气泡打字机渲染）+ 底部输入框 + 发送按钮（流式期间禁用）
- [x] WS 客户端封装：短连接建立（header 带 JWT）→ 发首帧 messages → 按 event 分发 → done/close 收尾；错误/超时 → 失败气泡 + 重试
- [x] 工具调用轨迹：tool_call 帧 → 灰色提示条；累积为 TOOL 消息随轮次落库，历史会话重渲染
- [x] 确认卡片组件：嵌入对话流（标题 + 信息 + 确认/取消按钮），card 帧不落库，仅当轮展示
- [x] 卡片点击后调对应确认接口（挂号确认 / 购药确认，action 为 Java C 端接口完整路径），展示成功/失败态
- [x] 对话历史：后端为准加载当前会话消息；本地缓存（storage key=sessionId）兜底同页切换
- [x] 会话列表弹层：标题 + 更新时间 + 新建会话 + 删除（confirm 后 DELETE）
- [x] AI 免责声明：每条 AI 回复底部标注"AI 建议仅供参考，不能替代医生诊断"
