package com.smartmed.backend.agent.service;

/**
 * Agent 服务不可用（网关层异常，09 ticket，ADR-0014）。
 * <p>
 * 与业务异常不同：SSE 网关失败契约是 HTTP 502（Java 失败走状态码，不产 error 事件），
 * 因此不走 GlobalExceptionHandler 的 "HTTP 200 + body.code" 惯例，
 * 由 ChatGatewayController 捕获后直接返回 HTTP 502。
 */
public class AgentUnavailableException extends RuntimeException {

    public AgentUnavailableException(String message) {
        super(message);
    }

    public AgentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
