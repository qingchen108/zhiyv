/**
 * 对话 WebSocket 短连接客户端（ticket 10，ADR-0014 修订）。
 *
 * 每次发送建一条 WS（/api/c/chat/ws，握手 header 带 JWT），
 * 连接建立后发送首帧 {"messages":[...]}，按 5 事件协议（delta/tool_call/card/done/error）分发，
 * done 后服务端 close(1000)；转发失败 close(1011)；30s 无事件本地提示断开。
 */

const WS_BASE_URL = 'ws://192.168.100.128:8080';

/** 5 事件协议帧（与 Python SSE 事件语义一致，Java 只做格式转换）。 */
export interface WsFrameData {
  delta: { text: string };
  tool_call: { tool: string; label: string };
  card: { type: string; title: string; action: string; payload: Record<string, unknown> };
  done: Record<string, never>;
  error: { message: string };
}

export type WsEvent = keyof WsFrameData;

export type WsFrame =
  | { event: 'delta'; data: WsFrameData['delta'] }
  | { event: 'tool_call'; data: WsFrameData['tool_call'] }
  | { event: 'card'; data: WsFrameData['card'] }
  | { event: 'done'; data: WsFrameData['done'] }
  | { event: 'error'; data: WsFrameData['error'] };

/** 请求载荷消息（全量历史，role 与 Python ChatMessage 一致）。 */
export interface WsRequestMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatWsHandlers {
  /** delta：AI 回复增量文本（逐字渲染）。 */
  onDelta: (text: string) => void;
  /** tool_call：工具调用轨迹（灰色提示条）。 */
  onToolCall: (data: WsFrameData['tool_call']) => void;
  /** card：确认卡片（点击后前端直调 Java）。 */
  onCard: (data: WsFrameData['card']) => void;
  /** done：流正常结束。 */
  onDone: () => void;
  /** error：Python 侧错误事件。 */
  onError: (message: string) => void;
  /** 连接异常关闭（Java 转发失败 1011 / 网络异常），按 code 展示兜底文案。 */
  onClosed: (code: number) => void;
}

export interface ChatWsHandle {
  /** 主动关闭并注销监听（页面卸载时调用）。 */
  close: () => void;
}

/** 正常关闭码：done 后服务端 close(1000) / 客户端主动 close。 */
const NORMAL_CODES = [1000, 1001, 1005];

/**
 * 发起一轮流式对话（短连接）。
 *
 * @param token    C 端 JWT（握手 header）
 * @param messages 全量历史（最新一条为本次用户输入）
 * @param handlers 事件回调
 * @param idleTimeoutMs 无事件超时（默认 30s，契约值）
 */
export function startChatStream(
  token: string,
  messages: WsRequestMessage[],
  handlers: ChatWsHandlers,
  idleTimeoutMs = 30000,
): ChatWsHandle {
  let settled = false; // done/error/超时/关闭 任一后置位，避免重复收尾
  let closed = false; // 主动关闭标志
  let idleTimer: number | null = null;

  const clearIdle = () => {
    if (idleTimer !== null) {
      clearTimeout(idleTimer);
      idleTimer = null;
    }
  };

  const armIdle = () => {
    clearIdle();
    idleTimer = setTimeout(() => {
      if (!settled) {
        settled = true;
        my.closeSocket();
        handlers.onError('响应超时，请重试');
      }
    }, idleTimeoutMs);
  };

  const cleanup = () => {
    clearIdle();
    my.offSocketOpen(onOpen);
    my.offSocketMessage(onMessage);
    my.offSocketClose(onClose);
    my.offSocketError(onSocketError);
  };

  const onOpen = () => {
    // 连接建立后发送首帧（全量历史，无状态）
    my.sendSocketMessage({
      data: JSON.stringify({ messages }),
      fail: () => {
        if (!settled) {
          settled = true;
          handlers.onError('消息发送失败，请重试');
        }
      },
    });
    armIdle();
  };

  const onMessage = (res: { data: string }) => {
    if (settled) {
      return;
    }
    let frame: WsFrame;
    try {
      frame = JSON.parse(res.data);
    } catch (e) {
      console.error('WS 帧解析失败', res.data, e);
      return;
    }
    armIdle(); // 任何事件都重置 30s 无事件计时
    switch (frame.event) {
      case 'delta':
        handlers.onDelta(frame.data.text);
        break;
      case 'tool_call':
        handlers.onToolCall(frame.data);
        break;
      case 'card':
        handlers.onCard(frame.data);
        break;
      case 'done':
        settled = true;
        handlers.onDone();
        my.closeSocket(); // 服务端也会关，双保险
        break;
      case 'error':
        settled = true;
        handlers.onError(frame.data.message || '服务暂时不可用');
        my.closeSocket();
        break;
      default:
        console.warn('未知 WS 事件', frame);
    }
  };

  const onClose = (res: { code: number }) => {
    if (settled) {
      return;
    }
    settled = true;
    // 非正常关闭（Java 转发失败 1011 等）→ 通知页面显示失败气泡
    if (!NORMAL_CODES.includes(res.code)) {
      handlers.onClosed(res.code);
    }
  };

  const onSocketError = () => {
    if (!settled) {
      settled = true;
      my.closeSocket();
      handlers.onError('网络异常，请重试');
    }
  };

  // 先注册监听再建连（避免错过 open 事件）
  my.onSocketOpen(onOpen);
  my.onSocketMessage(onMessage);
  my.onSocketClose(onClose);
  my.onSocketError(onSocketError);
  my.connectSocket({
    url: `${WS_BASE_URL}/api/c/chat/ws`,
    header: { Authorization: `Bearer ${token}` },
    fail: () => {
      if (!settled) {
        settled = true;
        handlers.onError('连接失败，请检查网络');
      }
    },
  });

  return {
    close() {
      if (closed) {
        return;
      }
      closed = true;
      settled = true;
      clearIdle();
      my.closeSocket();
      cleanup();
    },
  };
}
