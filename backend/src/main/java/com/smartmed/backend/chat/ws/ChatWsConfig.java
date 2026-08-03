package com.smartmed.backend.chat.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册（ticket 10，ADR-0014 修订）。
 * <p>
 * 端点 {@code /api/c/chat/ws}：短连接模式，每次发送建一条 WS，done 后关闭。
 * 握手鉴权由 {@link ChatWsHandshakeInterceptor} 完成（SecurityConfig 对该路径 permitAll，
 * WS 传输阶段不经 HTTP 过滤器链，鉴权职责完全在握手层）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatWsConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatWsHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/api/c/chat/ws")
                .addInterceptors(handshakeInterceptor)
                // 演示环境无跨域限制（CORS 已全局放行，见 CorsConfig）
                .setAllowedOriginPatterns("*");
    }
}
