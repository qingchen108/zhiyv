package com.smartmed.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 02 鉴权集成测试。
 * <p>
 * 直连 VM 上的真实 PostgreSQL（01 已建好 schema + 种子数据，含 admin/doctor BCrypt 哈希与 patient.id=1 演示患者）。
 * 本机无 Docker，故不用 Testcontainers（见 ticket 测试偏差说明）。测试只读，不污染数据。
 * <p>
 * 6 个场景（02 ticket）：
 * 1. B 端登录成功：返回 token + role + expiresIn=43200
 * 2. B 端登录密码错：code=401，message="用户名或密码错误"
 * 3. 无 token 访问 /api/b/auth/me：code=401
 * 4. C 端 token 访问 /api/b/**：code=403（typ 不匹配）
 * 5. 健康检查：公开访问，status=UP
 * 6. demo-login：返回 token + patientName="演示患者" + patientId=1
 */
@SpringBootTest(properties = {
        // 02 测试不依赖 Neo4j/Redis，排除其自动配置避免启动时连接探测失败
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 测试固定指向 VM，不依赖本机环境变量
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
        "AGENT_SECRET=smartmed-dev-agent-secret-change-me"
})
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokenProvider;

    // 1. B 端登录成功
    @Test
    void bLogin_success_returnsTokenRoleExpiresIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.doctorId").doesNotExist()) // ADMIN 为 null，序列化省略
                .andExpect(jsonPath("$.data.expiresIn").value(43200))
                .andReturn();
        // 验证 token 可解析
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String token = data.get("token").asText();
        org.junit.jupiter.api.Assertions.assertNotNull(tokenProvider.parse(token));
    }

    // 2. B 端登录密码错
    @Test
    void bLogin_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // 3. 无 token 访问 /api/b/auth/me
    @Test
    void bMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/b/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"));
    }

    // 4. C 端 token 访问 /api/b/** -> 403（typ 不匹配）
    @Test
    void bMe_withCToken_returns403() throws Exception {
        String cToken = tokenProvider.issueCToken(1L);
        mockMvc.perform(get("/api/b/auth/me")
                        .header("Authorization", "Bearer " + cToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问"));
    }

    // 5. 健康检查
    @Test
    void health_isPublic_returnsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.timestamp").isNotEmpty());
    }

    // 6. demo-login
    @Test
    void demoLogin_returnsTokenPatientNamePatientId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/c/auth/demo-login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.patientId").value(1))
                .andExpect(jsonPath("$.data.patientName").value("演示患者"))
                .andExpect(jsonPath("$.data.expiresIn").value(604800))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String token = data.get("token").asText();
        org.junit.jupiter.api.Assertions.assertNotNull(tokenProvider.parse(token));
    }
}
