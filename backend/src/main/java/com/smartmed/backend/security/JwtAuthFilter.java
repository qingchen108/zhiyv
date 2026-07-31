package com.smartmed.backend.security;

import com.smartmed.backend.common.GlobalExceptionHandler;
import com.smartmed.backend.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 过滤器（ADR-0003）。
 * <p>
 * 解析 token 并注入 {@link UserPrincipal} 到 SecurityContext。
 * token 缺失或无效时：若路径为受保护端点则直接写 401 JSON（HTTP status 仍 200，见 GlobalExceptionHandler）；
 * 若为公开端点则放行，交由 SecurityFilterChain 后续决策。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 未登录/token 失效统一提示（供过滤器与 SecurityConfig 入口点复用）。 */
    public static final String UNAUTHENTICATED_MSG = "未登录或登录已过期";

    private final JwtTokenProvider tokenProvider;
    private final GlobalExceptionHandler exceptionHandler;
    private final RefreshTokenService refreshTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            UserPrincipal principal = tokenProvider.parse(token);
            if (principal != null) {
                // ADR-0013 实时吊销：B 端 access token 需所属会话（refresh_jti）仍在 Redis。
                // logout / 改密 / 重用检测 后立即失效，而非等 30min 自然过期。
                if (JwtTokenProvider.TYP_B.equals(principal.getTyp())
                        && !refreshTokenService.isSessionActive(principal.getRefreshJti())) {
                    exceptionHandler.writeJson(response, Result.error(401, UNAUTHENTICATED_MSG));
                    return;
                }
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // token 无效：受保护端点直接写 401，不进入授权链
                if (isProtected(request)) {
                    exceptionHandler.writeJson(response, Result.error(401, UNAUTHENTICATED_MSG));
                    return;
                }
            }
        } else if (isProtected(request)) {
            // 无 token 访问受保护端点：直接写 401，避免落入授权链返回 HTTP 403
            exceptionHandler.writeJson(response, Result.error(401, UNAUTHENTICATED_MSG));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 判断是否为受保护端点（需 access token 的 B/C 端路径）。
     * 与 SecurityConfig 的公开 matcher 保持一致：排除 /api/c/auth/demo-login、/api/b/auth/refresh。
     */
    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/c/auth/demo-login".equals(path) || "/api/b/auth/refresh".equals(path)) {
            return false;
        }
        return path.startsWith("/api/b/") || path.startsWith("/api/c/");
    }
}
