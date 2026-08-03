package com.smartmed.backend.agent.controller;

import com.smartmed.backend.agent.dto.ChatStreamRequest;
import com.smartmed.backend.agent.service.AgentGateway;
import com.smartmed.backend.agent.service.AgentUnavailableException;
import com.smartmed.backend.security.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * C 端 Agent 对话网关（09 ticket，ADR-0014）。
 * <p>
 * POST /api/c/chat/stream：需 C 端 JWT（SecurityConfig /api/c/** 已拦，SecurityUtil 取操作人）。
 * 从 JWT 解析 patientId 注入 X-Patient-Id 头，带 X-Agent-Secret 转发 Python Agent 服务。
 * <p>
 * 失败契约例外（ADR-0014）：Agent 服务不可达/超时/非 200 → HTTP 502，
 * 不走项目 "HTTP 200 + body.code" 惯例（SSE 客户端按状态码区分成功/失败）。
 */
@Slf4j
@RestController
@RequestMapping("/api/c/chat")
@RequiredArgsConstructor
public class ChatGatewayController {

    private final AgentGateway agentGateway;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody ChatStreamRequest request) {
        String patientId = String.valueOf(SecurityUtil.current().getPatientId());
        try {
            return agentGateway.stream(patientId, request);
        } catch (AgentUnavailableException e) {
            log.warn("Agent 网关失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .build();
        }
    }
}
