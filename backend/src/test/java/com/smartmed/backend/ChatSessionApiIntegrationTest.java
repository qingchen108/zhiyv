package com.smartmed.backend;

import com.smartmed.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 10 对话会话/消息存储 API 集成测试（ticket 10，CONTEXT §8）。
 * <p>
 * 直连 VM（DB 配置同 AuthIntegrationTest）。用例自建数据并清理：
 * <ol>
 *   <li>无 token 访问会话接口 → code=401</li>
 *   <li>创建会话（title）→ 返回 id/title</li>
 *   <li>追加消息（USER+TOOL+ASSISTANT 批量）→ 200；列表按 created_at 升序、toolTrace 原样返回</li>
 *   <li>他人 token 访问会话/消息/删除 → 一律 404（不暴露存在性）</li>
 *   <li>删除会话 → 级联删消息（再查消息 404）</li>
 *   <li>请求体校验：title 空 / messages 空 / role 非法 → code=400</li>
 * </ol>
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
class ChatSessionApiIntegrationTest {

    /** 演示患者（demo-login 固定 patient.id=1）。 */
    private static final long DEMO_PATIENT = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider tokenProvider;

    /** 本测试创建的会话 id（AfterEach 清理，避免污染演示数据）。 */
    private final List<Long> createdSessionIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdSessionIds) {
            try {
                mockMvc.perform(delete("/api/c/chat/sessions/" + id)
                        .header("Authorization", "Bearer " + tokenProvider.issueCToken(DEMO_PATIENT)));
            } catch (Exception ignored) {
                // 清理失败不影响断言结果
            }
        }
        createdSessionIds.clear();
    }

    // 1. 无 token → 401
    @Test
    void sessions_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/c/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    // 2. 创建会话 + 列表
    @Test
    void createAndListSession() throws Exception {
        String token = cToken();
        String title = "测试会话-" + System.currentTimeMillis();

        MvcResult created = mockMvc.perform(post("/api/c/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value(title))
                .andReturn();
        long sessionId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).path("data").path("id").asLong();
        createdSessionIds.add(sessionId);

        mockMvc.perform(get("/api/c/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").exists());
    }

    // 3. 批量追加 + 消息列表（toolTrace 原样返回，顺序升序）
    @Test
    void appendAndListMessages() throws Exception {
        String token = cToken();
        long sessionId = createSession(token, "消息测试");
        try {
            mockMvc.perform(post("/api/c/chat/sessions/" + sessionId + "/messages")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"messages":[
                                      {"role":"USER","content":"我有点头疼"},
                                      {"role":"TOOL","content":"正在查询知识图谱...","toolTrace":"{\\"tool\\":\\"query_knowledge_graph\\",\\"label\\":\\"正在查询知识图谱...\\"}"},
                                      {"role":"ASSISTANT","content":"建议您去神经内科就诊"}
                                    ]}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get("/api/c/chat/sessions/" + sessionId + "/messages")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.length()").value(3))
                    .andExpect(jsonPath("$.data[0].role").value("USER"))
                    .andExpect(jsonPath("$.data[1].role").value("TOOL"))
                    .andExpect(jsonPath("$.data[1].toolTrace").value(org.hamcrest.Matchers.containsString("query_knowledge_graph")))
                    .andExpect(jsonPath("$.data[2].role").value("ASSISTANT"));
        } finally {
            cleanupOne(sessionId);
        }
    }

    // 4. 他人访问 → 404（会话/消息/删除）
    @Test
    void othersSession_returns404() throws Exception {
        String token = cToken();
        long sessionId = createSession(token, "归属测试");
        try {
            String otherToken = tokenProvider.issueCToken(2L);
            mockMvc.perform(get("/api/c/chat/sessions/" + sessionId + "/messages")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(jsonPath("$.code").value(404));
            mockMvc.perform(post("/api/c/chat/sessions/" + sessionId + "/messages")
                            .header("Authorization", "Bearer " + otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"messages\":[{\"role\":\"USER\",\"content\":\"hi\"}]}"))
                    .andExpect(jsonPath("$.code").value(404));
            mockMvc.perform(delete("/api/c/chat/sessions/" + sessionId)
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(jsonPath("$.code").value(404));
        } finally {
            cleanupOne(sessionId);
        }
    }

    // 5. 删除会话级联删消息
    @Test
    void deleteSession_cascadesMessages() throws Exception {
        String token = cToken();
        long sessionId = createSession(token, "级联删除测试");
        mockMvc.perform(post("/api/c/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"USER\",\"content\":\"hi\"}]}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/api/c/chat/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 删除后再查消息/会话 → 404（会话已不存在）
        mockMvc.perform(get("/api/c/chat/sessions/" + sessionId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // 6. 请求体校验
    @Test
    void invalidBody_returns400() throws Exception {
        String token = cToken();
        // title 空
        mockMvc.perform(post("/api/c/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(jsonPath("$.code").value(400));
        // messages 空
        mockMvc.perform(post("/api/c/chat/sessions/1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(jsonPath("$.code").value(400));
        // role 非法
        mockMvc.perform(post("/api/c/chat/sessions/1/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"SYSTEM\",\"content\":\"hi\"}]}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    private String cToken() {
        return tokenProvider.issueCToken(DEMO_PATIENT);
    }

    private long createSession(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/c/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long id = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertTrue(id > 0, "创建会话应返回正 id");
        return id;
    }

    private void cleanupOne(long sessionId) {
        try {
            mockMvc.perform(delete("/api/c/chat/sessions/" + sessionId)
                    .header("Authorization", "Bearer " + cToken()));
        } catch (Exception ignored) {
        }
    }
}
