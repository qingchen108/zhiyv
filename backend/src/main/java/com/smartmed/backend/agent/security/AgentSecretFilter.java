package com.smartmed.backend.agent.security;

import com.smartmed.backend.agent.config.AgentProperties;
import com.smartmed.backend.common.GlobalExceptionHandler;
import com.smartmed.backend.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Agent 工具路由鉴权过滤器（09 ticket，ADR-0015）。
 * <p>
 * 只拦 {@code /api/agent/tools/**}（工具契约由 Python Agent 侧调用）：
 * 校验 {@code X-Agent-Secret}，与配置密钥做常量时间比较（MessageDigest.isEqual，防时序攻击）。
 * 失败写统一响应体 code=401（HTTP status 仍 200，ADR-0003），成功放行进入 Dispatcher。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSecretFilter extends OncePerRequestFilter {

    /** 请求头名。 */
    public static final String HEADER = "X-Agent-Secret";
    /** 鉴权失败统一提示（供测试断言复用）。 */
    public static final String UNAUTHORIZED_MSG = "X-Agent-Secret 缺失或错误";

    private final AgentProperties agentProperties;
    private final GlobalExceptionHandler exceptionHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/agent/tools/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(provided, agentProperties.getSecret())) {
            log.warn("Agent 工具路由鉴权失败: path={}", request.getRequestURI());
            exceptionHandler.writeJson(response, Result.error(401, UNAUTHORIZED_MSG));
            return;
        }
        chain.doFilter(request, response);
    }

    /** 常量时间比较：长度不一致时同样完整执行 isEqual，不提前返回。 */
    private boolean constantTimeEquals(String provided, String expected) {
        if (expected == null) {
            return false;
        }
        byte[] a = provided.getBytes(StandardCharsets.UTF_8);
        byte[] b = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
