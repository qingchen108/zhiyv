/**
 * 智愈健康 - AI 对话页（ticket 10，CONTEXT §8 / ADR-0014 修订）。
 *
 * - 传输：WebSocket 短连接（utils/chatWs.ts），5 事件协议（delta/tool_call/card/done/error）
 * - 存储：done 后每轮批量原子保存（user + tool×N + assistant）；失败轮次不落库
 * - 会话：顶栏弹层切换/新建/删除；后端为准加载历史，本地缓存（storage key=sessionId）兜底
 * - 交互：串行发送（流式期间禁用输入）；失败气泡 + 重试（原样重发该轮）
 */
import {
  createChatSession,
  listChatSessions,
  getChatMessages,
  appendChatMessages,
  deleteChatSession,
  ChatSessionItem,
  ChatMessageItem,
} from '../../utils/chatApi';
import { startChatStream, ChatWsHandle, WsRequestMessage } from '../../utils/chatWs';
import { request } from '../../utils/request';

/** 本地消息模型（渲染 + 流式状态）。 */
interface ChatItem {
  key: string;
  role: 'USER' | 'ASSISTANT' | 'TOOL';
  content: string;
  /** TOOL 消息轨迹（tool_call 帧数据）。 */
  toolTrace?: { tool: string; label: string };
  /** ASSISTANT 消息内的确认卡片（card 帧，不落库）。 */
  card?: {
    type: string;
    title: string;
    action: string;
    payload: Record<string, unknown>;
    status?: 'idle' | 'loading' | 'success' | 'fail';
    result?: string;
  };
  /** 流式进行中（打字机占位）。 */
  streaming?: boolean;
  /** 本轮失败（失败气泡 + 重试按钮，不落库）。 */
  failed?: boolean;
  errorText?: string;
  createdAt?: string;
}

/** 会话缓存结构（本地兜底渲染，后端仍为准）。 */
interface SessionCache {
  messages: ChatItem[];
}

const CACHE_PREFIX = 'chat_cache_';
const LAST_SESSION_KEY = 'chat_last_session_id';

const DISCLAIMER = 'AI 建议仅供参考，不能替代医生诊断';

Page({
  data: {
    messages: [] as ChatItem[],
    inputValue: '',
    sending: false,
    sessionId: 0,
    sessionTitle: '',
    showSessions: false,
    sessions: [] as ChatSessionItem[],
    loadingHistory: false,
    scrollIntoView: '',
  },

  /** 当前流式连接句柄（onUnload 时关闭）。 */
  chatHandle: null as ChatWsHandle | null,

  onLoad() {
    this.loadSessions();
    this.restoreLastSession();
  },

  onShow() {
    this.loadSessions();
  },

  onUnload() {
    if (this.chatHandle) {
      this.chatHandle.close();
      this.chatHandle = null;
    }
  },

  // ==================== 会话管理 ====================

  /** 加载会话列表（弹层数据源，静默失败）。 */
  loadSessions() {
    listChatSessions()
      .then((res) => {
        if (res.code === 200 && res.data) {
          this.setData({ sessions: res.data });
        }
      })
      .catch(() => {
        console.warn('会话列表加载失败');
      });
  },

  /** 恢复上次会话（本地记忆，进入页面直接续聊）。 */
  restoreLastSession() {
    const sessionId = my.getStorageSync({ key: LAST_SESSION_KEY }).data;
    if (sessionId) {
      this.loadSession(Number(sessionId));
    }
  },

  /** 加载会话历史：本地缓存兜底快速渲染 → 后端为准覆盖。 */
  loadSession(sessionId: number) {
    this.setData({ loadingHistory: true, sessionId });
    my.setStorageSync({ key: LAST_SESSION_KEY, data: sessionId });

    // 1. 本地缓存兜底（同页/重进快速渲染）
    const cached = my.getStorageSync({ key: `${CACHE_PREFIX}${sessionId}` }).data as SessionCache | undefined;
    if (cached && cached.messages && cached.messages.length > 0) {
      this.setData({ messages: cached.messages, loadingHistory: false });
    } else {
      this.setData({ messages: [] });
    }

    // 2. 后端为准（chat_message 是单一事实来源）
    getChatMessages(sessionId)
      .then((res) => {
        if (res.code === 200 && res.data) {
          const items = this.mapHistory(res.data);
          this.setData({ messages: items });
          this.cacheSession(sessionId, items);
          const session = this.data.sessions.find((s) => s.id === sessionId);
          if (session) {
            this.setData({ sessionTitle: session.title });
          }
        } else if (res.code === 404) {
          // 会话已删除：清空本地
          this.clearLocalSession(sessionId);
        }
      })
      .catch(() => {
        // 后端不可达时保留缓存渲染
      })
      .finally(() => {
        this.setData({ loadingHistory: false });
        this.scrollToBottom();
      });
  },

  /** 历史消息 → 本地渲染模型。 */
  mapHistory(list: ChatMessageItem[]): ChatItem[] {
    return list.map((m) => {
      const item: ChatItem = {
        key: `h${m.id}`,
        role: m.role,
        content: m.content,
        createdAt: m.createdAt,
      };
      if (m.role === 'TOOL' && m.toolTrace) {
        try {
          item.toolTrace = JSON.parse(m.toolTrace);
        } catch {
          item.toolTrace = { tool: '', label: m.content };
        }
      }
      return item;
    });
  },

  /** 写入本地缓存（后端为准，缓存仅兜底；card 为一次性交互 UI，不缓存不重渲染）。 */
  cacheSession(sessionId: number, messages: ChatItem[]) {
    const clean = messages.map((m) => (m.card ? { ...m, card: undefined } : m));
    my.setStorageSync({ key: `${CACHE_PREFIX}${sessionId}`, data: { messages: clean } });
  },

  clearLocalSession(sessionId: number) {
    my.removeStorageSync({ key: `${CACHE_PREFIX}${sessionId}` });
  },

  /** 新建会话（清空当前视图，发送首条消息时才真正创建）。 */
  onNewSession() {
    this.setData({
      sessionId: 0,
      sessionTitle: '',
      messages: [],
      showSessions: false,
      scrollIntoView: '',
    });
    my.removeStorageSync({ key: LAST_SESSION_KEY });
  },

  /** 弹层切换会话。 */
  onSwitchSession(e: any) {
    const id = Number(e.currentTarget.dataset.id);
    this.setData({ showSessions: false });
    if (id !== this.data.sessionId) {
      this.loadSession(id);
    }
  },

  /** 删除会话（confirm 后 DELETE，级联删消息）。 */
  onDeleteSession(e: any) {
    const id = Number(e.currentTarget.dataset.id);
    my.confirm({
      title: '删除会话',
      content: '删除后该会话记录将不可恢复，确定删除？',
      confirmButtonText: '删除',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        deleteChatSession(id)
          .then((r) => {
            if (r.code === 200) {
              this.loadSessions();
              this.clearLocalSession(id);
              if (id === this.data.sessionId) {
                this.onNewSession();
              }
              my.showToast({ content: '已删除', type: 'success' });
            } else {
              my.showToast({ content: r.message || '删除失败', type: 'none' });
            }
          })
          .catch(() => {
            my.showToast({ content: '删除失败，请重试', type: 'none' });
          });
      },
    });
  },

  openSessions() {
    this.loadSessions();
    this.setData({ showSessions: true });
  },

  closeSessions() {
    this.setData({ showSessions: false });
  },

  // ==================== 输入与发送 ====================

  onInput(e: any) {
    this.setData({ inputValue: e.detail.value });
  },

  /** 输入框发送。 */
  onSend() {
    const content = (this.data.inputValue || '').trim();
    if (!content || this.data.sending) {
      return;
    }
    this.setData({ inputValue: '' });
    this.sendRound(content);
  },

  /** 失败重试：移除失败轮次渲染（tool/card/失败气泡），原样重发该轮。 */
  onRetry(e: any) {
    const index = Number(e.currentTarget.dataset.index);
    const messages = this.data.messages.slice();
    const failed = messages[index];
    if (!failed || !failed.failed) {
      return;
    }
    // 找该轮 user 消息（failed AI 之前最近的 USER）
    let userIndex = -1;
    for (let i = index - 1; i >= 0; i--) {
      if (messages[i].role === 'USER') {
        userIndex = i;
        break;
      }
      if (messages[i].role === 'ASSISTANT' && !messages[i].failed && !messages[i].streaming) {
        break; // 越过上一轮边界
      }
    }
    if (userIndex < 0) {
      return;
    }
    const userContent = messages[userIndex].content;
    // 移除 user 之后本轮所有渲染（tool/card/failed AI）
    this.setData({
      messages: messages.slice(0, userIndex + 1),
      sending: true,
    });
    this.sendRound(userContent);
  },

  /**
   * 发送一轮对话（核心链路）。
   * 首次发送时先创建会话（title=首条消息截断 ≤20 字），再建 WS 流。
   * 调用方（onSend/onRetry）已做 sending 互斥，此处不再自锁检查。
   */
  sendRound(userContent: string) {
    this.setData({ sending: true });

    const doSend = () => {
      this.setData({ sessionTitle: this.data.sessionTitle || this.truncateTitle(userContent) });
      this.startStream(userContent);
    };

    if (!this.data.sessionId) {
      createChatSession(this.truncateTitle(userContent))
        .then((res) => {
          if (res.code === 200 && res.data) {
            const sessionId = res.data.id;
            this.setData({ sessionId, sessionTitle: res.data.title || this.truncateTitle(userContent) });
            my.setStorageSync({ key: LAST_SESSION_KEY, data: sessionId });
            doSend();
          } else {
            this.failRound(res.message || '会话创建失败');
          }
        })
        .catch(() => {
          this.failRound('会话创建失败，请重试');
        });
    } else {
      doSend();
    }
  },

  /** 构造本轮渲染消息并启动 WS 流。 */
  startStream(userContent: string) {
    const now = Date.now();
    const userItem: ChatItem = { key: `u${now}`, role: 'USER', content: userContent };
    const aiItem: ChatItem = {
      key: `a${now}`,
      role: 'ASSISTANT',
      content: '',
      streaming: true,
    };
    const messages = this.data.messages.concat([userItem, aiItem]);
    this.setData({ messages });
    this.scrollToBottom();

    // 全量历史载荷（TOOL/streaming/failed 不参与；role 对齐 Python user|assistant）
    const payload: WsRequestMessage[] = messages
      .filter((m) => m.role !== 'TOOL' && !m.streaming && !m.failed)
      .map((m) => ({ role: m.role === 'USER' ? 'user' : 'assistant', content: m.content }));

    const token = my.getStorageSync({ key: 'token' }).data || '';
    const sessionId = this.data.sessionId;
    const aiKey = aiItem.key;
    const roundTools: Array<{ tool: string; label: string }> = [];

    const updateMessage = (key: string, patch: Partial<ChatItem>) => {
      const list = this.data.messages.map((m) => (m.key === key ? { ...m, ...patch } : m));
      this.setData({ messages: list });
    };

    this.chatHandle = startChatStream(
      token,
      payload,
      {
        onDelta: (text: string) => {
          const list = this.data.messages.map((m) =>
            m.key === aiKey ? { ...m, content: m.content + text } : m,
          );
          this.setData({ messages: list });
          this.scrollToBottom();
        },
        onToolCall: (data: { tool: string; label: string }) => {
          roundTools.push(data);
          const list = this.data.messages.concat([
            { key: `t${Date.now()}${this.data.messages.length}`, role: 'TOOL', content: data.label, toolTrace: data },
          ]);
          this.setData({ messages: list });
          this.scrollToBottom();
        },
        onCard: (data: { type: string; title: string; action: string; payload: Record<string, unknown> }) => {
          const list = this.data.messages.concat([
            {
              key: `c${Date.now()}`,
              role: 'ASSISTANT',
              content: '',
              card: { ...data, status: 'idle' },
            },
          ]);
          this.setData({ messages: list });
          this.scrollToBottom();
        },
        onDone: () => {
          updateMessage(aiKey, { streaming: false });
          this.completeRound(sessionId, userContent, roundTools);
        },
        onError: (message: string) => {
          updateMessage(aiKey, { streaming: false, failed: true, errorText: message });
          this.setData({ sending: false });
          this.scrollToBottom();
        },
        onClosed: (code: number) => {
          updateMessage(aiKey, {
            streaming: false,
            failed: true,
            errorText: code === 1011 ? '服务暂时不可用，请稍后重试' : '连接中断，请重试',
          });
          this.setData({ sending: false });
          this.scrollToBottom();
        },
      },
      30000,
    );
  },

  /** done 后收尾：批量原子保存本轮（user + tool×N + assistant），失败轮次不落库。 */
  completeRound(sessionId: number, userContent: string, tools: Array<{ tool: string; label: string }>) {
    // 取本轮 AI 文本：排除 card 消息（content 恒为空，且不落库）
    const ai = [...this.data.messages]
      .reverse()
      .find((m) => m.role === 'ASSISTANT' && !m.streaming && !m.failed && !m.card);
    const assistantContent = ai ? ai.content : '';
    const batch = [
      { role: 'USER' as const, content: userContent },
      ...tools.map((t) => ({
        role: 'TOOL' as const,
        content: t.label,
        toolTrace: JSON.stringify(t),
      })),
      { role: 'ASSISTANT' as const, content: assistantContent },
    ];
    this.setData({ sending: false });
    appendChatMessages(sessionId, batch)
      .then((res) => {
        if (res.code === 200) {
          // 保存成功：同步本地缓存 + 会话列表时间
          this.cacheSession(sessionId, this.data.messages);
          this.loadSessions();
        } else {
          my.showToast({ content: res.message || '消息保存失败', type: 'none' });
        }
      })
      .catch(() => {
        my.showToast({ content: '消息保存失败', type: 'none' });
      });
  },

  /** 会话创建失败等前置失败：AI 占位标失败。 */
  failRound(message: string) {
    const now = Date.now();
    const list = this.data.messages.concat([
      { key: `a${now}`, role: 'ASSISTANT', content: '', failed: true, errorText: message },
    ]);
    this.setData({ messages: list, sending: false });
    this.scrollToBottom();
  },

  // ==================== 确认卡片 ====================

  /** 卡片确认/取消：action 为 Java C 端接口完整路径，payload 为草稿权威 JSON，前端直调 Java。 */
  onCardTap(e: any) {
    const index = Number(e.currentTarget.dataset.index);
    const card = this.data.messages[index]?.card;
    if (!card || card.status === 'loading') {
      return;
    }
    this.setCardStatus(index, 'loading', '');
    request({
      url: card.action,
      method: 'POST',
      data: card.payload,
    })
      .then((res) => {
        if (res.code === 200) {
          this.setCardStatus(index, 'success', '操作成功');
        } else {
          this.setCardStatus(index, 'fail', res.message || '操作失败');
        }
      })
      .catch(() => {
        this.setCardStatus(index, 'fail', '网络异常，请重试');
      });
  },

  /** 卡片取消：一次性交互 UI，取消即移除该卡片（catchTap 防冒泡触发确认）。 */
  onCardCancel(e: any) {
    const index = Number(e.currentTarget.dataset.index);
    const list = this.data.messages.filter((_, i) => i !== index);
    this.setData({ messages: list });
  },

  setCardStatus(index: number, status: 'loading' | 'success' | 'fail', result: string) {
    const list = this.data.messages.map((m, i) => {
      if (i === index && m.card) {
        return { ...m, card: { ...m.card, status, result } };
      }
      return m;
    });
    this.setData({ messages: list });
  },

  // ==================== 工具方法 ====================

  /** 首条消息截断 ≤20 字作为会话标题。 */
  truncateTitle(content: string): string {
    const trimmed = content.trim().replace(/\s+/g, ' ');
    return trimmed.length > 20 ? `${trimmed.slice(0, 20)}…` : trimmed;
  },

  scrollToBottom() {
    const list = this.data.messages;
    if (list.length > 0) {
      this.setData({ scrollIntoView: `msg-${list[list.length - 1].key}` });
    }
  },
});
