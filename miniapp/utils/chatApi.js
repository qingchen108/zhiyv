"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createChatSession = createChatSession;
exports.listChatSessions = listChatSessions;
exports.getChatMessages = getChatMessages;
exports.appendChatMessages = appendChatMessages;
exports.deleteChatSession = deleteChatSession;
/** 对话会话/消息 API（ticket 10，CONTEXT §8）。 */
var request_1 = require("./request");
/** 创建会话（首条消息时调用，title=首条消息截断 ≤20 字）。 */
function createChatSession(title) {
    return (0, request_1.request)({ url: '/api/c/chat/sessions', method: 'POST', data: { title: title } });
}
/** 会话列表（updated_at 倒序）。 */
function listChatSessions() {
    return (0, request_1.request)({ url: '/api/c/chat/sessions' });
}
/** 会话消息列表（created_at 升序）。 */
function getChatMessages(sessionId) {
    return (0, request_1.request)({ url: "/api/c/chat/sessions/".concat(sessionId, "/messages") });
}
/** 批量追加消息（done 后每轮一次原子保存：user + tool×N + assistant）。 */
function appendChatMessages(sessionId, messages) {
    return (0, request_1.request)({ url: "/api/c/chat/sessions/".concat(sessionId, "/messages"), method: 'POST', data: { messages: messages } });
}
/** 删除会话（物理删除 + 级联删消息）。 */
function deleteChatSession(sessionId) {
    return (0, request_1.request)({ url: "/api/c/chat/sessions/".concat(sessionId), method: 'DELETE' });
}
