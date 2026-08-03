package com.smartmed.backend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.agent.config.AgentProperties;
import com.smartmed.backend.agent.dto.ChatStreamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Agent 网关（09 ticket，ADR-0014）。
 * <p>
 * 转发 C 端对话到 Python Agent 服务，SSE 流字节级透传（不解析不重组）：
 * <ul>
 *   <li>JDK HttpClient.send() 同步等待响应头 + BodyHandlers.ofInputStream() 留流</li>
 *   <li>HttpRequest.timeout = 首 token 上限（默认 60s），拿到响应头后流式不限时</li>
 *   <li>请求带 X-Agent-Secret（双向鉴权）+ X-Patient-Id（操作人，Java 注入）</li>
 *   <li>连接失败/超时/非 200 → AgentUnavailableException（Controller 转 HTTP 502）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentGateway {

    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 转发对话请求并透传 SSE 流。
     *
     * @param patientId 操作人 patientId（来自 C 端 JWT）
     * @param request   对话请求（含全量历史）
     * @return SSE 响应（StreamingResponseBody 字节级透传）
     */
    public ResponseEntity<StreamingResponseBody> stream(String patientId, ChatStreamRequest request) {
        URI uri = URI.create(agentProperties.getBaseUrl() + "/agent/chat");
        HttpRequest httpRequest = buildHttpRequest(uri, patientId, request);

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            log.warn("Agent 服务连接失败: url={}", uri, e);
            throw new AgentUnavailableException("Agent 服务不可达");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentUnavailableException("请求被中断");
        }

        if (response.statusCode() != HttpStatus.OK.value()) {
            try (InputStream ignored = response.body()) {
                // 关闭错误响应体释放连接
            } catch (IOException e) {
                log.debug("关闭 Agent 错误响应体失败", e);
            }
            log.warn("Agent 服务返回异常状态: http={}", response.statusCode());
            throw new AgentUnavailableException("Agent 服务错误: HTTP " + response.statusCode());
        }

        StreamingResponseBody srb = outputStream -> {
            try (InputStream in = response.body()) {
                in.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(srb);
    }

    private HttpRequest buildHttpRequest(URI uri, String patientId, ChatStreamRequest request) {
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (IOException e) {
            throw new AgentUnavailableException("请求序列化失败", e);
        }
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(agentProperties.getFirstTokenTimeoutMs()))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("X-Agent-Secret", agentProperties.getSecret())
                .header("X-Patient-Id", patientId)
                .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                .build();
    }
}
