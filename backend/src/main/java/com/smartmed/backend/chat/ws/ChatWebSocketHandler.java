package com.smartmed.backend.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.agent.dto.ChatStreamRequest;
import com.smartmed.backend.agent.service.AgentGateway;
import com.smartmed.backend.agent.service.AgentUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * C 端对话 WebSocket 网关（ticket 10，ADR-0014 修订）。
 * <p>
 * 短连接模式：客户端建连后首帧发送 {@code {"messages":[...]}}（与 /stream 请求体同构），
 * Java 消费 Python SSE 流逐块转为 WS 帧 {@code {"event":"...","data":{...}}} 推给小程序，
 * 收到 done 后主动 close(1000)。事件字段与 SSE 5 事件协议完全一致，Java 不解析业务语义。
 * <p>
 * 失败语义：Python 未启动/超时 → close(1011)（Java 不发 error 帧，error 为 Python 专属事件）；
 * 前端 onClose 按 code 显示兜底文案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /** Java 转发失败关闭码（对应旧 SSE 语义的 HTTP 502，ADR-0014）。 */
    public static final CloseStatus AGENT_UNAVAILABLE = new CloseStatus(1011, "Agent 服务不可用");

    private final AgentGateway agentGateway;
    private final ObjectMapper objectMapper;

    /** 短连接转发任务线程池（阻塞式 SSE 读取，不占用容器 IO 线程）。 */
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "chat-ws-forward");
        t.setDaemon(true);
        return t;
    });

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String patientId = String.valueOf(session.getAttributes().get(ChatWsHandshakeInterceptor.ATTR_PATIENT_ID));
        ChatStreamRequest request;
        try {
            request = objectMapper.readValue(message.getPayload(), ChatStreamRequest.class);
        } catch (IOException e) {
            log.warn("WS 首帧解析失败: {}", e.getMessage());
            closeQuietly(session, new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "首帧格式错误"));
            return;
        }
        if (request.messages() == null || request.messages().isEmpty()) {
            log.warn("WS 首帧 messages 为空");
            closeQuietly(session, new CloseStatus(CloseStatus.POLICY_VIOLATION.getCode(), "messages 不能为空"));
            return;
        }

        // 并发装饰器保证跨线程 sendMessage 安全（任务在 executor 线程读取 SSE 并发送）
        ConcurrentWebSocketSessionDecorator safeSession =
                new ConcurrentWebSocketSessionDecorator(session, 10_000, 512 * 1024);
        executor.submit(() -> forward(safeSession, patientId, request));
    }

    /** 消费 Python SSE 流逐块转 WS 帧，done 后关闭；转发失败 close 1011。 */
    private void forward(ConcurrentWebSocketSessionDecorator session, String patientId, ChatStreamRequest request) {
        try (InputStream in = agentGateway.openStream(patientId, request);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder eventName = new StringBuilder();
            StringBuilder dataLines = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    // SSE 块结束：发一帧（忽略无 event 名的块）
                    if (eventName.length() > 0) {
                        sendFrame(session, eventName.toString(), dataLines.toString());
                        // done 帧已发送：主动关闭，不再等 Python 断流（ADR-0014）
                        if ("done".equals(eventName.toString())) {
                            closeQuietly(session, CloseStatus.NORMAL);
                            return;
                        }
                    }
                    eventName.setLength(0);
                    dataLines.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName.setLength(0);
                    eventName.append(line.substring("event:".length()).trim());
                } else if (line.startsWith("data:")) {
                    if (dataLines.length() > 0) {
                        dataLines.append('\n');
                    }
                    dataLines.append(line.substring("data:".length()).trim());
                }
                // 其他行（注释等）忽略
            }
            // 流正常结束（Python 侧已发 done）：由 done 帧触发关闭，此处兜底
            closeQuietly(session, CloseStatus.NORMAL);
        } catch (AgentUnavailableException e) {
            log.warn("WS 转发失败（Agent 不可用）: {}", e.getMessage());
            closeQuietly(session, AGENT_UNAVAILABLE);
        } catch (IOException e) {
            if (session.isOpen()) {
                log.warn("WS 转发 IO 异常: {}", e.getMessage());
                closeQuietly(session, AGENT_UNAVAILABLE);
            }
        } catch (Exception e) {
            log.error("WS 转发未知异常", e);
            closeQuietly(session, AGENT_UNAVAILABLE);
        }
    }

    /** 组装 {"event": "...", "data": {...}} 帧并发送（data 为原样 JSON，不解析语义）。 */
    private void sendFrame(WebSocketSession session, String event, String dataJson) throws IOException {
        try {
            JsonNode data = dataJson.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(dataJson);
            session.sendMessage(new TextMessage(buildFrame(event, data)));
            log.debug("WS 转发帧: event={}", event);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            // data 非合法 JSON：按字符串原样透传（协议扩展兜底）
            session.sendMessage(new TextMessage(buildFrame(event, objectMapper.getNodeFactory().textNode(dataJson))));
        }
    }

    /** 有序构造帧 JSON（ObjectNode 保持插入顺序：event 在前，data 在后）。 */
    private String buildFrame(String event, JsonNode data) {
        var frame = objectMapper.createObjectNode();
        frame.put("event", event);
        frame.set("data", data);
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (IOException e) {
            throw new IllegalStateException("帧序列化失败", e);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (IOException e) {
            log.debug("WS 关闭异常: {}", e.getMessage());
        }
    }
}
