package com.smartmed.backend.security;

import com.smartmed.backend.agent.security.AgentSecretFilter;
import com.smartmed.backend.common.GlobalExceptionHandler;
import com.smartmed.backend.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（ADR-0003）。
 * <p>
 * 单条 SecurityFilterChain，路径分权：
 * <ul>
 *   <li>{@code /api/auth/**}、{@code /api/b/auth/refresh}、{@code /api/health} 公开</li>
 *   <li>{@code /api/b/**} 需 typ=B（角色由 @PreAuthorize 补充）；refresh 需 typ=B_RT，在 AuthService 内解析</li>
 *   <li>{@code /api/c/**} 需 typ=C</li>
 *   <li>{@code /api/agent/tools/**} permitAll + AgentSecretFilter 校验 X-Agent-Secret（09 接手，ADR-0015）</li>
 *   <li>其他拒绝</li>
 * </ul>
 * JWT 无状态；session 禁用；CSRF 禁用（无状态 API）。
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AgentSecretFilter agentSecretFilter;
    private final TypAuthorizationManager typAuthorizationManager;
    private final GlobalExceptionHandler exceptionHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 对齐 01 种子数据 BCrypt 哈希（$2b$10$...）
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 公开端点：B 端登录、refresh 换发（无 access token，靠 refresh token 自身）、C 端 demo-login、健康检查
                        .requestMatchers("/api/auth/**", "/api/b/auth/refresh", "/api/c/auth/demo-login", "/api/health").permitAll()
                        // Agent 工具路由：permitAll + AgentSecretFilter 校验 X-Agent-Secret（09，ADR-0015）
                        .requestMatchers("/api/agent/tools/**").permitAll()
                        // 对话 WS 端点：握手请求放行，鉴权由 ChatWsHandshakeInterceptor 完成（10，ADR-0014 修订）
                        .requestMatchers("/api/c/chat/ws").permitAll()
                        // B/C 端：需认证 + typ 匹配（access 绑定 TypAuthorizationManager 做前缀分权）
                        .requestMatchers("/api/b/**", "/api/c/**").access(typAuthorizationManager)
                        // 其他拒绝
                        .anyRequest().denyAll()
                )
                // 401 未认证 / 403 越权：均写统一响应体（HTTP 200，body.code 区分，ADR-0003）
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                // JWT 过滤器在 UsernamePasswordAuthenticationFilter 之前
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Agent 工具路由密钥校验（09）：与 JWT 无关，置于 JWT 过滤器之后
                .addFilterAfter(agentSecretFilter, JwtAuthFilter.class);

        return http.build();
    }

    /** 401 处理：返回 { code:401, message }，HTTP status 恒 200。复用 GlobalExceptionHandler.writeJson。 */
    private org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                exceptionHandler.writeJson(response, Result.error(401, JwtAuthFilter.UNAUTHENTICATED_MSG));
    }

    /** 403 处理：返回 { code:403, message:"无权访问" }，HTTP status 恒 200。复用 GlobalExceptionHandler.writeJson。 */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                exceptionHandler.writeJson(response, Result.error(403, "无权访问"));
    }
}
