"use strict";
/**
 * 对话 WebSocket 短连接客户端（ticket 10，ADR-0014 修订）。
 *
 * 每次发送建一条 WS（/api/c/chat/ws，握手 header 带 JWT），
 * 连接建立后发送首帧 {"messages":[...]}，按 5 事件协议（delta/tool_call/card/done/error）分发，
 * done 后服务端 close(1000)；转发失败 close(1011)；30s 无事件本地提示断开。
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.startChatStream = startChatStream;
var WS_BASE_URL = 'ws://192.168.100.128:8080';
/** 正常关闭码：done 后服务端 close(1000) / 客户端主动 close。 */
var NORMAL_CODES = [1000, 1001, 1005];
/**
 * 发起一轮流式对话（短连接）。
 *
 * @param token    C 端 JWT（握手 header）
 * @param messages 全量历史（最新一条为本次用户输入）
 * @param handlers 事件回调
 * @param idleTimeoutMs 无事件超时（默认 30s，契约值）
 */
function startChatStream(token, messages, handlers, idleTimeoutMs) {
    if (idleTimeoutMs === void 0) { idleTimeoutMs = 30000; }
    var settled = false; // done/error/超时/关闭 任一后置位，避免重复收尾
    var closed = false; // 主动关闭标志
    var idleTimer = null;
    var clearIdle = function () {
        if (idleTimer !== null) {
            clearTimeout(idleTimer);
            idleTimer = null;
        }
    };
    var armIdle = function () {
        clearIdle();
        idleTimer = setTimeout(function () {
            if (!settled) {
                settled = true;
                my.closeSocket();
                handlers.onError('响应超时，请重试');
            }
        }, idleTimeoutMs);
    };
    var cleanup = function () {
        clearIdle();
        my.offSocketOpen(onOpen);
        my.offSocketMessage(onMessage);
        my.offSocketClose(onClose);
        my.offSocketError(onSocketError);
    };
    var onOpen = function () {
        // 连接建立后发送首帧（全量历史，无状态）
        my.sendSocketMessage({
            data: JSON.stringify({ messages: messages }),
            fail: function () {
                if (!settled) {
                    settled = true;
                    handlers.onError('消息发送失败，请重试');
                }
            },
        });
        armIdle();
    };
    var onMessage = function (res) {
        if (settled) {
            return;
        }
        var frame;
        try {
            frame = JSON.parse(res.data);
        }
        catch (e) {
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
    var onClose = function (res) {
        if (settled) {
            return;
        }
        settled = true;
        // 非正常关闭（Java 转发失败 1011 等）→ 通知页面显示失败气泡
        if (!NORMAL_CODES.includes(res.code)) {
            handlers.onClosed(res.code);
        }
    };
    var onSocketError = function () {
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
        url: "".concat(WS_BASE_URL, "/api/c/chat/ws"),
        header: { Authorization: "Bearer ".concat(token) },
        fail: function () {
            if (!settled) {
                settled = true;
                handlers.onError('连接失败，请检查网络');
            }
        },
    });
    return {
        close: function () {
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
