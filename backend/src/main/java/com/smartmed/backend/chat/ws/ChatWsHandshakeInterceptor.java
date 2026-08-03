package com.smartmed.backend.chat.ws;

import com.smartmed.backend.security.JwtTokenProvider;
import com.smartmed.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WS 握手鉴权拦截器（ticket 10，ADR-0014 修订）。
 * <p>
 * 支付宝小程序 {@code my.connectSocket} 握手时通过 header 携带
 * {@code Authorization: Bearer <C 端 JWT>}（07 已验证支持 header 字段）。
 * 校验 typ=C 后把 patientId 放入 attributes 供 {@link ChatWebSocketHandler} 注入
 * {@code X-Patient-Id}；校验失败拒绝握手（HTTP 401）。
 * <p>
 * 注意：/api/c/chat/ws 在 SecurityConfig 中 permitAll，WS 传输阶段不经过 HTTP
 * 过滤器链，因此鉴权职责完全落在本拦截器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWsHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_PATIENT_ID = "patientId";

    private final JwtTokenProvider tokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            log.warn("WS 握手拒绝: 缺少 Authorization header");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        UserPrincipal principal = tokenProvider.parse(header.substring(7));
        if (principal == null || !JwtTokenProvider.TYP_C.equals(principal.getTyp())) {
            log.warn("WS 握手拒绝: JWT 无效或非 C 端 token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_PATIENT_ID, principal.getPatientId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
