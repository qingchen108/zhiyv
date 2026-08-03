package com.smartmed.backend;

import com.smartmed.backend.agent.dto.ChatStreamRequest;
import com.smartmed.backend.agent.service.AgentGateway;
import com.smartmed.backend.agent.service.AgentUnavailableException;
import com.smartmed.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 10 对话 WebSocket 网关集成测试（ticket 10，ADR-0014 修订）。
 * <p>
 * RANDOM_PORT 启动真实容器，StandardWebSocketClient 直连：
 * <ol>
 *   <li>无 Authorization header 握手 → 拒绝（401）</li>
 *   <li>有效 C 端 JWT + mock SSE 流 → 逐块收到 {"event","data"} 帧（delta→done），服务端 close 1000</li>
 *   <li>Agent 不可用（openStream 抛异常）→ 服务端 close 1011（Java 不发 error 帧）</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration"
})
@TestPropertySource(properties = {
        "DB_HOST=192.168.100.128",
        "DB_PORT=5432",
        "DB_USER=smartmed",
        "DB_PASSWORD=smartmed",
        "DB_NAME=smartmed",
        "REDIS_HOST=192.168.100.128",
        "REDIS_PORT=6379",
        "REDIS_PASSWORD=smartmed",
        "NEO4J_URI=bolt://192.168.100.128:7687",
        "NEO4J_USER=neo4j",
        "NEO4J_PASSWORD=smartmed",
        "JWT_SECRET=smartmed-dev-jwt-secret-change-me",
        "AGENT_SECRET=smartmed-dev-agent-secret-change-me",
        "AGENT_BASE_URL=http://localhost:8000"
})
class ChatWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private AgentGateway agentGateway;

    /** 连接建立后发送首帧（与 /stream 请求体同构）。 */
    private void sendFirstFrame(WebSocketSession session) throws Exception {
        session.sendMessage(new TextMessage(
                "{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"));
    }

    // 1. 无 token 握手 → 拒绝
    @Test
    void handshake_withoutToken_rejected() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        String url = "ws://localhost:" + port + "/api/c/chat/ws";
        assertThrows(Exception.class,
                () -> client.execute(new CollectingHandler(), url).get(5, TimeUnit.SECONDS));
    }

    // 2. 有效 token + SSE 流 → 帧序列 delta→done，close 1000
    @Test
    void handshake_withToken_forwardsFramesAndCloses() throws Exception {
        String sse = "event: delta\ndata: {\"text\":\"你\"}\n\n"
                + "event: delta\ndata: {\"text\":\"好\"}\n\n"
                + "event: done\ndata: {}\n\n";
        when(agentGateway.openStream(eq("1"), any(ChatStreamRequest.class)))
                .thenReturn(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + tokenProvider.issueCToken(1L));

        CollectingHandler handler = new CollectingHandler();
        WebSocketSession session = client.execute(handler, headers,
                        URI.create("ws://localhost:" + port + "/api/c/chat/ws"))
                .get(5, TimeUnit.SECONDS);
        sendFirstFrame(session);

        assertTrue(handler.closed.await(10, TimeUnit.SECONDS), "服务端应主动关闭");
        List<String> frames = handler.collectFrames();
        assertEquals(3, frames.size(), "应收到 2 个 delta + 1 个 done");
        assertEquals("{\"event\":\"delta\",\"data\":{\"text\":\"你\"}}", frames.get(0));
        assertEquals("{\"event\":\"delta\",\"data\":{\"text\":\"好\"}}", frames.get(1));
        assertTrue(frames.get(2).contains("\"event\":\"done\""), "done 帧应包含 event=done");
        assertEquals(CloseStatus.NORMAL.getCode(), handler.closeCode, "正常结束应 close 1000");
    }

    // 3. Agent 不可用 → close 1011（Java 不发 error 帧）
    @Test
    void agentDown_closesWith1011() throws Exception {
        doThrow(new AgentUnavailableException("Agent 服务不可达"))
                .when(agentGateway).openStream(eq("1"), any(ChatStreamRequest.class));

        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + tokenProvider.issueCToken(1L));

        CollectingHandler handler = new CollectingHandler();
        WebSocketSession session = client.execute(handler, headers,
                        URI.create("ws://localhost:" + port + "/api/c/chat/ws"))
                .get(5, TimeUnit.SECONDS);
        sendFirstFrame(session);

        assertTrue(handler.closed.await(10, TimeUnit.SECONDS), "服务端应主动关闭");
        assertTrue(handler.collectFrames().isEmpty(), "Java 转发失败不应发任何帧（error 为 Python 专属）");
        assertEquals(1011, handler.closeCode, "转发失败应 close 1011");
    }

    /** 收集帧与关闭事件的 WS 客户端处理器。 */
    private static class CollectingHandler extends TextWebSocketHandler {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile int closeCode = -1;

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeCode = status.getCode();
            closed.countDown();
        }

        List<String> collectFrames() {
            java.util.ArrayList<String> list = new java.util.ArrayList<>();
            frames.drainTo(list);
            return list;
        }
    }
}
