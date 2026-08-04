"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
var __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * 智愈健康 - AI 对话页（ticket 10，CONTEXT §8 / ADR-0014 修订）。
 *
 * - 传输：WebSocket 短连接（utils/chatWs.ts），5 事件协议（delta/tool_call/card/done/error）
 * - 存储：done 后每轮批量原子保存（user + tool×N + assistant）；失败轮次不落库
 * - 会话：顶栏弹层切换/新建/删除；后端为准加载历史，本地缓存（storage key=sessionId）兜底
 * - 交互：串行发送（流式期间禁用输入）；失败气泡 + 重试（原样重发该轮）
 */
var chatApi_1 = require("../../utils/chatApi");
var chatWs_1 = require("../../utils/chatWs");
var request_1 = require("../../utils/request");
var CACHE_PREFIX = 'chat_cache_';
var LAST_SESSION_KEY = 'chat_last_session_id';
var DISCLAIMER = 'AI 建议仅供参考，不能替代医生诊断';
Page({
    data: {
        messages: [],
        inputValue: '',
        sending: false,
        sessionId: 0,
        sessionTitle: '',
        showSessions: false,
        sessions: [],
        loadingHistory: false,
        scrollIntoView: '',
    },
    /** 当前流式连接句柄（onUnload 时关闭）。 */
    chatHandle: null,
    onLoad: function () {
        this.loadSessions();
        this.restoreLastSession();
    },
    onShow: function () {
        this.loadSessions();
    },
    onUnload: function () {
        if (this.chatHandle) {
            this.chatHandle.close();
            this.chatHandle = null;
        }
    },
    // ==================== 会话管理 ====================
    /** 加载会话列表（弹层数据源，静默失败）。 */
    loadSessions: function () {
        var _this = this;
        (0, chatApi_1.listChatSessions)()
            .then(function (res) {
            if (res.code === 200 && res.data) {
                _this.setData({ sessions: res.data });
            }
        })
            .catch(function () {
            console.warn('会话列表加载失败');
        });
    },
    /** 恢复上次会话（本地记忆，进入页面直接续聊）。 */
    restoreLastSession: function () {
        var sessionId = my.getStorageSync({ key: LAST_SESSION_KEY }).data;
        if (sessionId) {
            this.loadSession(Number(sessionId));
        }
    },
    /** 加载会话历史：本地缓存兜底快速渲染 → 后端为准覆盖。 */
    loadSession: function (sessionId) {
        var _this = this;
        this.setData({ loadingHistory: true, sessionId: sessionId });
        my.setStorageSync({ key: LAST_SESSION_KEY, data: sessionId });
        // 1. 本地缓存兜底（同页/重进快速渲染）
        var cached = my.getStorageSync({ key: "".concat(CACHE_PREFIX).concat(sessionId) }).data;
        if (cached && cached.messages && cached.messages.length > 0) {
            this.setData({ messages: cached.messages, loadingHistory: false });
        }
        else {
            this.setData({ messages: [] });
        }
        // 2. 后端为准（chat_message 是单一事实来源）
        (0, chatApi_1.getChatMessages)(sessionId)
            .then(function (res) {
            if (res.code === 200 && res.data) {
                var items = _this.mapHistory(res.data);
                _this.setData({ messages: items });
                _this.cacheSession(sessionId, items);
                var session = _this.data.sessions.find(function (s) { return s.id === sessionId; });
                if (session) {
                    _this.setData({ sessionTitle: session.title });
                }
            }
            else if (res.code === 404) {
                // 会话已删除：清空本地
                _this.clearLocalSession(sessionId);
            }
        })
            .catch(function () {
            // 后端不可达时保留缓存渲染
        })
            .finally(function () {
            _this.setData({ loadingHistory: false });
            _this.scrollToBottom();
        });
    },
    /** 历史消息 → 本地渲染模型。 */
    mapHistory: function (list) {
        return list.map(function (m) {
            var item = {
                key: "h".concat(m.id),
                role: m.role,
                content: m.content,
                createdAt: m.createdAt,
            };
            if (m.role === 'TOOL' && m.toolTrace) {
                try {
                    item.toolTrace = JSON.parse(m.toolTrace);
                }
                catch (_a) {
                    item.toolTrace = { tool: '', label: m.content };
                }
            }
            return item;
        });
    },
    /** 写入本地缓存（后端为准，缓存仅兜底；card 为一次性交互 UI，不缓存不重渲染）。 */
    cacheSession: function (sessionId, messages) {
        var clean = messages.map(function (m) { return (m.card ? __assign(__assign({}, m), { card: undefined }) : m); });
        my.setStorageSync({ key: "".concat(CACHE_PREFIX).concat(sessionId), data: { messages: clean } });
    },
    clearLocalSession: function (sessionId) {
        my.removeStorageSync({ key: "".concat(CACHE_PREFIX).concat(sessionId) });
    },
    /** 新建会话（清空当前视图，发送首条消息时才真正创建）。 */
    onNewSession: function () {
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
    onSwitchSession: function (e) {
        var id = Number(e.currentTarget.dataset.id);
        this.setData({ showSessions: false });
        if (id !== this.data.sessionId) {
            this.loadSession(id);
        }
    },
    /** 删除会话（confirm 后 DELETE，级联删消息）。 */
    onDeleteSession: function (e) {
        var _this = this;
        var id = Number(e.currentTarget.dataset.id);
        my.confirm({
            title: '删除会话',
            content: '删除后该会话记录将不可恢复，确定删除？',
            confirmButtonText: '删除',
            success: function (res) {
                if (!res.confirm) {
                    return;
                }
                (0, chatApi_1.deleteChatSession)(id)
                    .then(function (r) {
                    if (r.code === 200) {
                        _this.loadSessions();
                        _this.clearLocalSession(id);
                        if (id === _this.data.sessionId) {
                            _this.onNewSession();
                        }
                        my.showToast({ content: '已删除', type: 'success' });
                    }
                    else {
                        my.showToast({ content: r.message || '删除失败', type: 'none' });
                    }
                })
                    .catch(function () {
                    my.showToast({ content: '删除失败，请重试', type: 'none' });
                });
            },
        });
    },
    openSessions: function () {
        this.loadSessions();
        this.setData({ showSessions: true });
    },
    closeSessions: function () {
        this.setData({ showSessions: false });
    },
    // ==================== 输入与发送 ====================
    onInput: function (e) {
        this.setData({ inputValue: e.detail.value });
    },
    /** 输入框发送。 */
    onSend: function () {
        var content = (this.data.inputValue || '').trim();
        if (!content || this.data.sending) {
            return;
        }
        this.setData({ inputValue: '' });
        this.sendRound(content);
    },
    /** 失败重试：移除失败轮次渲染（tool/card/失败气泡），原样重发该轮。 */
    onRetry: function (e) {
        var index = Number(e.currentTarget.dataset.index);
        var messages = this.data.messages.slice();
        var failed = messages[index];
        if (!failed || !failed.failed) {
            return;
        }
        // 找该轮 user 消息（failed AI 之前最近的 USER）
        var userIndex = -1;
        for (var i = index - 1; i >= 0; i--) {
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
        var userContent = messages[userIndex].content;
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
    sendRound: function (userContent) {
        var _this = this;
        this.setData({ sending: true });
        var doSend = function () {
            _this.setData({ sessionTitle: _this.data.sessionTitle || _this.truncateTitle(userContent) });
            _this.startStream(userContent);
        };
        if (!this.data.sessionId) {
            (0, chatApi_1.createChatSession)(this.truncateTitle(userContent))
                .then(function (res) {
                if (res.code === 200 && res.data) {
                    var sessionId = res.data.id;
                    _this.setData({ sessionId: sessionId, sessionTitle: res.data.title || _this.truncateTitle(userContent) });
                    my.setStorageSync({ key: LAST_SESSION_KEY, data: sessionId });
                    doSend();
                }
                else {
                    _this.failRound(res.message || '会话创建失败');
                }
            })
                .catch(function () {
                _this.failRound('会话创建失败，请重试');
            });
        }
        else {
            doSend();
        }
    },
    /** 构造本轮渲染消息并启动 WS 流。 */
    startStream: function (userContent) {
        var _this = this;
        var now = Date.now();
        var userItem = { key: "u".concat(now), role: 'USER', content: userContent };
        var aiItem = {
            key: "a".concat(now),
            role: 'ASSISTANT',
            content: '',
            streaming: true,
        };
        var messages = this.data.messages.concat([userItem, aiItem]);
        this.setData({ messages: messages });
        this.scrollToBottom();
        // 全量历史载荷（TOOL/streaming/failed 不参与；role 对齐 Python user|assistant）
        var payload = messages
            .filter(function (m) { return m.role !== 'TOOL' && !m.streaming && !m.failed; })
            .map(function (m) { return ({ role: m.role === 'USER' ? 'user' : 'assistant', content: m.content }); });
        var token = my.getStorageSync({ key: 'token' }).data || '';
        var sessionId = this.data.sessionId;
        var aiKey = aiItem.key;
        var roundTools = [];
        var updateMessage = function (key, patch) {
            var list = _this.data.messages.map(function (m) { return (m.key === key ? __assign(__assign({}, m), patch) : m); });
            _this.setData({ messages: list });
        };
        this.chatHandle = (0, chatWs_1.startChatStream)(token, payload, {
            onDelta: function (text) {
                var list = _this.data.messages.map(function (m) {
                    return m.key === aiKey ? __assign(__assign({}, m), { content: m.content + text }) : m;
                });
                _this.setData({ messages: list });
                _this.scrollToBottom();
            },
            onToolCall: function (data) {
                roundTools.push(data);
                var list = _this.data.messages.concat([
                    { key: "t".concat(Date.now()).concat(_this.data.messages.length), role: 'TOOL', content: data.label, toolTrace: data },
                ]);
                _this.setData({ messages: list });
                _this.scrollToBottom();
            },
            onCard: function (data) {
                var list = _this.data.messages.concat([
                    {
                        key: "c".concat(Date.now()),
                        role: 'ASSISTANT',
                        content: '',
                        card: __assign(__assign({}, data), { status: 'idle' }),
                    },
                ]);
                _this.setData({ messages: list });
                _this.scrollToBottom();
            },
            onDone: function () {
                updateMessage(aiKey, { streaming: false });
                _this.completeRound(sessionId, userContent, roundTools);
            },
            onError: function (message) {
                updateMessage(aiKey, { streaming: false, failed: true, errorText: message });
                _this.setData({ sending: false });
                _this.scrollToBottom();
            },
            onClosed: function (code) {
                updateMessage(aiKey, {
                    streaming: false,
                    failed: true,
                    errorText: code === 1011 ? '服务暂时不可用，请稍后重试' : '连接中断，请重试',
                });
                _this.setData({ sending: false });
                _this.scrollToBottom();
            },
        }, 30000);
    },
    /** done 后收尾：批量原子保存本轮（user + tool×N + assistant），失败轮次不落库。 */
    completeRound: function (sessionId, userContent, tools) {
        var _this = this;
        // 取本轮 AI 文本：排除 card 消息（content 恒为空，且不落库）
        var ai = __spreadArray([], this.data.messages, true).reverse()
            .find(function (m) { return m.role === 'ASSISTANT' && !m.streaming && !m.failed && !m.card; });
        var assistantContent = ai ? ai.content : '';
        var batch = __spreadArray(__spreadArray([
            { role: 'USER', content: userContent }
        ], tools.map(function (t) { return ({
            role: 'TOOL',
            content: t.label,
            toolTrace: JSON.stringify(t),
        }); }), true), [
            { role: 'ASSISTANT', content: assistantContent },
        ], false);
        this.setData({ sending: false });
        (0, chatApi_1.appendChatMessages)(sessionId, batch)
            .then(function (res) {
            if (res.code === 200) {
                // 保存成功：同步本地缓存 + 会话列表时间
                _this.cacheSession(sessionId, _this.data.messages);
                _this.loadSessions();
            }
            else {
                my.showToast({ content: res.message || '消息保存失败', type: 'none' });
            }
        })
            .catch(function () {
            my.showToast({ content: '消息保存失败', type: 'none' });
        });
    },
    /** 会话创建失败等前置失败：AI 占位标失败。 */
    failRound: function (message) {
        var now = Date.now();
        var list = this.data.messages.concat([
            { key: "a".concat(now), role: 'ASSISTANT', content: '', failed: true, errorText: message },
        ]);
        this.setData({ messages: list, sending: false });
        this.scrollToBottom();
    },
    // ==================== 确认卡片 ====================
    /** 卡片确认/取消：action 为 Java C 端接口完整路径，payload 为草稿权威 JSON，前端直调 Java。 */
    onCardTap: function (e) {
        var _this = this;
        var _a;
        var index = Number(e.currentTarget.dataset.index);
        var card = (_a = this.data.messages[index]) === null || _a === void 0 ? void 0 : _a.card;
        if (!card || card.status === 'loading') {
            return;
        }
        this.setCardStatus(index, 'loading', '');
        (0, request_1.request)({
            url: card.action,
            method: 'POST',
            data: card.payload,
        })
            .then(function (res) {
            if (res.code === 200) {
                _this.setCardStatus(index, 'success', '挂号成功');
                // 追加 AI 成功提示文案（挂号场景）
                var voucherMsg = {
                    key: "v".concat(Date.now()),
                    role: 'ASSISTANT',
                    content: '✅ 挂号成功！请按时就诊。\n\n就诊前 1 天会提醒您，请保持手机畅通。\n\n🤖 AI 建议仅供参考，不能替代医生诊断。',
                };
                var list = _this.data.messages.concat([voucherMsg]);
                _this.setData({ messages: list });
                _this.scrollToBottom();
            }
            else {
                _this.setCardStatus(index, 'fail', res.message || '操作失败');
            }
        })
            .catch(function () {
            _this.setCardStatus(index, 'fail', '网络异常，请重试');
        });
    },
    /** 卡片取消：一次性交互 UI，取消即移除该卡片（catchTap 防冒泡触发确认）。 */
    onCardCancel: function (e) {
        var index = Number(e.currentTarget.dataset.index);
        var list = this.data.messages.filter(function (_, i) { return i !== index; });
        this.setData({ messages: list });
    },
    setCardStatus: function (index, status, result) {
        var list = this.data.messages.map(function (m, i) {
            if (i === index && m.card) {
                return __assign(__assign({}, m), { card: __assign(__assign({}, m.card), { status: status, result: result }) });
            }
            return m;
        });
        this.setData({ messages: list });
    },
    // ==================== 工具方法 ====================
    /** 首条消息截断 ≤20 字作为会话标题。 */
    truncateTitle: function (content) {
        var trimmed = content.trim().replace(/\s+/g, ' ');
        return trimmed.length > 20 ? "".concat(trimmed.slice(0, 20), "\u2026") : trimmed;
    },
    scrollToBottom: function () {
        var list = this.data.messages;
        if (list.length > 0) {
            this.setData({ scrollIntoView: "msg-".concat(list[list.length - 1].key) });
        }
    },
});
