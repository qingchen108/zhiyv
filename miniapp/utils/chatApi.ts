/** 对话会话/消息 API（ticket 10，CONTEXT §8）。 */
import { request } from './request';

/** 会话项（会话列表弹层展示：标题 + 更新时间）。 */
export interface ChatSessionItem {
  id: number;
  title: string;
  updatedAt: string;
}

/** 历史消息（后端为准加载）。 */
export interface ChatMessageItem {
  id: number;
  role: 'USER' | 'ASSISTANT' | 'TOOL';
  content: string;
  toolTrace?: string;
  createdAt: string;
}

/** 创建会话（首条消息时调用，title=首条消息截断 ≤20 字）。 */
export function createChatSession(title: string) {
  return request<ChatSessionItem>({ url: '/api/c/chat/sessions', method: 'POST', data: { title } });
}

/** 会话列表（updated_at 倒序）。 */
export function listChatSessions() {
  return request<ChatSessionItem[]>({ url: '/api/c/chat/sessions' });
}

/** 会话消息列表（created_at 升序）。 */
export function getChatMessages(sessionId: number) {
  return request<ChatMessageItem[]>({ url: `/api/c/chat/sessions/${sessionId}/messages` });
}

/** 批量追加消息（done 后每轮一次原子保存：user + tool×N + assistant）。 */
export function appendChatMessages(
  sessionId: number,
  messages: Array<{ role: 'USER' | 'ASSISTANT' | 'TOOL'; content: string; toolTrace?: string }>,
) {
  return request({ url: `/api/c/chat/sessions/${sessionId}/messages`, method: 'POST', data: { messages } });
}

/** 删除会话（物理删除 + 级联删消息）。 */
export function deleteChatSession(sessionId: number) {
  return request({ url: `/api/c/chat/sessions/${sessionId}`, method: 'DELETE' });
}
