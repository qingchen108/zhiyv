package com.smartmed.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 05 挂号全链路集成测试。
 * <p>
 * 直连 VM PostgreSQL + Redis，@Transactional 回滚隔离。
 * 覆盖场景：
 * 1. 创建草稿成功
 * 2. 确认挂号成功（完整链路）
 * 3. 防刷拦截（5秒内重复）
 * 4. 重复挂号拒绝
 * 5. 取消挂号成功
 * 6. 草稿不存在/过期 → 400
 * 7. 号源不足 → 400
 * 8. 挂号列表查询
 * 9. 挂号详情查询
 * 10. 停诊排班不可挂号
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
        "AGENT_SECRET=smartmed-dev-agent-secret-change-me"
})
@Transactional
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private String cToken() {
        return tokenProvider.issueCToken(1L);
    }

    private String adminToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"13800000000\",\"password\":\"admin123\"}"))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("token").asText();
    }

    private String tomorrow() {
        return LocalDate.now().plusDays(1).toString();
    }

    /** 创建排班并返回 schedule ID。 */
    private long createSchedule(String date, String period, int totalSlots) throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"doctorId\":1,\"departmentId\":2,\"scheduleDate\":\"" + date
                                + "\",\"timePeriod\":\"" + period + "\",\"totalSlots\":" + totalSlots + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("id").asLong();
    }

    /** 创建草稿并返回 confirmToken。 */
    private String createDraftAndGetToken(long scheduleId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("confirmToken").asText();
    }

    // 1. 创建草稿成功
    @Test
    void createDraft_success() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "MORNING", 20);
        mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.confirmToken").isNotEmpty())
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleId))
                .andExpect(jsonPath("$.data.doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.departmentName").value("呼吸内科"));
    }

    // 2. 确认挂号成功（完整链路）
    @Test
    void confirm_success_fullFlow() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "AFTERNOON", 10);
        String confirmToken = createDraftAndGetToken(scheduleId);

        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.regNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("REGISTERED"))
                .andExpect(jsonPath("$.data.doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.departmentName").value("呼吸内科"))
                .andExpect(jsonPath("$.data.visitorName").value("演示患者"))
                .andExpect(jsonPath("$.data.timePeriod").value("AFTERNOON"));
    }

    // 3. 防刷拦截（5秒内重复确认）
    @Test
    void confirm_rateLimited_returns400() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "EVENING", 10);
        String confirmToken = createDraftAndGetToken(scheduleId);

        // 第一次确认成功
        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200));

        // 立即再次创建草稿并确认（5秒内）→ 防刷拦截
        // 注意：因为重复挂号校验会先拦住，所以这里用另一个排班测防刷
        long scheduleId2 = createSchedule(tomorrow(), "MORNING", 10);
        String confirmToken2 = createDraftAndGetToken(scheduleId2);

        // 手动设置防刷 key 模拟 5 秒内重复
        redisTemplate.opsForValue().set("reg_ratelimit:self:" + scheduleId2, "1",
                java.time.Duration.ofSeconds(5));

        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId2 + ",\"confirmToken\":\"" + confirmToken2 + "\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("操作过于频繁，请5秒后重试"));
    }

    // 4. 重复挂号拒绝
    @Test
    void confirm_duplicate_returns400() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "MORNING", 10);
        String confirmToken = createDraftAndGetToken(scheduleId);

        // 第一次成功
        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200));

        // 清除防刷 key 以测试重复挂号校验
        redisTemplate.delete("reg_ratelimit:self:" + scheduleId);

        // 再次创建草稿 → 重复挂号校验在草稿阶段就拦住
        mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该就诊人已挂过此号，不可重复挂号"));
    }

    // 5. 取消挂号成功
    @Test
    void cancel_success() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "MORNING", 10);
        String confirmToken = createDraftAndGetToken(scheduleId);

        // 先挂号
        MvcResult r = mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        long regId = data.get("id").asLong();

        // 取消（明天的排班，距就诊 > 2h）
        mockMvc.perform(patch("/api/c/registrations/" + regId + "/cancel")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // 6. 草稿不存在 → 400
    @Test
    void confirm_noDraft_returns400() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "MORNING", 10);

        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"fake-token\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("草稿不存在或已过期，请重新挂号"));
    }

    // 7. 号源不足 → 400
    @Test
    void createDraft_noSlots_returns400() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "MORNING", 1);
        // 手动将 Redis 号源设为 0 模拟抢完
        redisTemplate.opsForValue().set("schedule:" + scheduleId + ":remaining_slots", "0");

        mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("号源已抢完"));
    }

    // 8. 挂号列表查询
    @Test
    void page_returnsList() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "AFTERNOON", 10);
        String confirmToken = createDraftAndGetToken(scheduleId);

        // 先挂一个
        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200));

        // 查询列表
        mockMvc.perform(get("/api/c/registrations")
                        .header("Authorization", "Bearer " + cToken())
                        .param("status", "REGISTERED"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    // 9. 挂号详情查询
    @Test
    void getById_success() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "EVENING", 10);
        String confirmToken = createDraftAndGetToken(scheduleId);

        MvcResult r = mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        long regId = data.get("id").asLong();

        mockMvc.perform(get("/api/c/registrations/" + regId)
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(regId))
                .andExpect(jsonPath("$.data.regNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("REGISTERED"));
    }

    // 10. 停诊排班不可挂号
    @Test
    void createDraft_suspended_returns400() throws Exception {
        long scheduleId = createSchedule(tomorrow(), "MORNING", 10);

        // 停诊
        String admin = adminToken();
        mockMvc.perform(patch("/api/b/schedules/" + scheduleId + "/suspend")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value(200));

        // 尝试挂号
        mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该排班已停诊，无法挂号"));
    }
}
