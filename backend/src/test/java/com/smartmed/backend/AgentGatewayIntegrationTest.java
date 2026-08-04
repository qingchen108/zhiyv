package com.smartmed.backend;

import com.smartmed.backend.agent.dto.ChatStreamRequest;
import com.smartmed.backend.agent.service.AgentGateway;
import com.smartmed.backend.agent.service.AgentUnavailableException;
import com.smartmed.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 09 Agent 网关集成测试（ADR-0014/0015）。
 * <p>
 * 直连 VM（DB/Redis 配置同 AuthIntegrationTest），仅校验 09 骨架行为：
 * <ol>
 *   <li>C 端无 token 访问 /api/c/chat/stream → code=401</li>
 *   <li>C 端 token + Agent 不可用 → HTTP 502（ADR-0014 失败契约）</li>
 *   <li>请求体校验：messages 为空 / role 非法 → code=400</li>
 *   <li>工具路由缺 X-Agent-Secret → code=401</li>
 *   <li>工具路由错误 secret → code=401</li>
 *   <li>工具路由正确 secret + 已知工具 → code=501（09 未实现）</li>
 *   <li>工具路由未知工具 → code=404</li>
 * </ol>
 * SSE 成功透传路径（StreamingResponseBody 字节级转发）不在 MockMvc 覆盖——
 * MockMvc 的 asyncDispatch 会重跑 Security 过滤器链导致 AccessDenied（真实容器不会），
 * 该行为由端到端 echo 验证（ticket 09 验收）覆盖。
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration"
})
@AutoConfigureMockMvc
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
class AgentGatewayIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @MockBean
    private AgentGateway agentGateway;

    // 1. C 端无 token 访问对话网关
    @Test
    void chatStream_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/c/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    // 2. C 端 token + Agent 服务不可用 → HTTP 502（ADR-0014：网关失败走状态码，不产 error 事件）
    @Test
    void chatStream_gatewayDown_returns502() throws Exception {
        doThrow(new AgentUnavailableException("Agent 服务不可达"))
                .when(agentGateway).stream(eq("1"), any(ChatStreamRequest.class));

        String cToken = tokenProvider.issueCToken(1L);
        mockMvc.perform(post("/api/c/chat/stream")
                        .header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"))
                .andExpect(status().isBadGateway());
    }

    // 3. 请求体校验：messages 为空
    @Test
    void chatStream_emptyMessages_returns400() throws Exception {
        String cToken = tokenProvider.issueCToken(1L);
        mockMvc.perform(post("/api/c/chat/stream")
                        .header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // 4. 请求体校验：role 非法
    @Test
    void chatStream_invalidRole_returns400() throws Exception {
        String cToken = tokenProvider.issueCToken(1L);
        mockMvc.perform(post("/api/c/chat/stream")
                        .header("Authorization", "Bearer " + cToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"system\",\"content\":\"hi\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // 5. 工具路由缺 secret
    @Test
    void agentTool_withoutSecret_returns401() throws Exception {
        mockMvc.perform(post("/api/agent/tools/query_departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("X-Agent-Secret 缺失或错误"));
    }

    // 6. 工具路由错误 secret
    @Test
    void agentTool_withWrongSecret_returns401() throws Exception {
        mockMvc.perform(post("/api/agent/tools/query_departments")
                        .header("X-Agent-Secret", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // 7. 正确 secret + query_schedule 已实现 → 200
    @Test
    void agentTool_querySchedule_returns200() throws Exception {
        mockMvc.perform(post("/api/agent/tools/query_schedule")
                        .header("X-Agent-Secret", "smartmed-dev-agent-secret-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 7b. 正确 secret + create_registration_draft 无 X-Patient-Id → 400
    @Test
    void agentTool_createRegistrationDraft_withoutPatientId_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/tools/create_registration_draft")
                        .header("X-Agent-Secret", "smartmed-dev-agent-secret-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{\"schedule_id\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // 7c. 正确 secret + create_registration_draft 含 X-Patient-Id → 200（调度无效，但不会 501）
    @Test
    void agentTool_createRegistrationDraft_withPatientId_returns200() throws Exception {
        mockMvc.perform(post("/api/agent/tools/create_registration_draft")
                        .header("X-Agent-Secret", "smartmed-dev-agent-secret-change-me")
                        .header("X-Patient-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{\"schedule_id\":1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 8. 正确 secret + 已实现工具 → 200
    @Test
    void agentTool_withSecret_implementedTool_returns200() throws Exception {
        mockMvc.perform(post("/api/agent/tools/query_departments")
                        .header("X-Agent-Secret", "smartmed-dev-agent-secret-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // 9. 未知工具 → 404
    @Test
    void agentTool_unknownTool_returns404() throws Exception {
        mockMvc.perform(post("/api/agent/tools/unknown_tool")
                        .header("X-Agent-Secret", "smartmed-dev-agent-secret-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("未知工具: unknown_tool"));
    }
}
