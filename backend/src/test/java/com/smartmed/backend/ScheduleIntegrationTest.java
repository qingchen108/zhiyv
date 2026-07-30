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
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 04 排班与号源管理集成测试（ADR-0009）。
 * <p>
 * 直连 VM PostgreSQL + Redis，@Transactional 回滚隔离。
 * 覆盖场景：
 * 1. 创建排班成功（即发布）
 * 2. 创建排班冲突（同医生同日同班次）→ 409
 * 3. 创建排班日期超窗口 → 400
 * 4. 创建排班无效班次 → 400
 * 5. 修改排班成功
 * 6. 修改 total 少于已用数 → 400
 * 7. 停诊成功
 * 8. 恢复成功
 * 9. 手动调整号源（加号）
 * 10. 手动调整号源（减到负数）→ 400
 * 11. 删除排班（无挂号）成功
 * 12. 分页查询
 * 13. 周复制
 * 14. DOCTOR 角色访问 → 403
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
class ScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokenProvider;

    private String adminToken() throws Exception {
        return login("13800000000", "admin123");
    }

    private String doctorToken() throws Exception {
        return login("13800000002", "doctor123");
    }

    private String login(String phone, String pwd) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + pwd + "\"}"))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("token").asText();
    }

    /** 获取下一个可用的排班日期（明天，确保在窗口内）。 */
    private String tomorrow() {
        return LocalDate.now().plusDays(1).toString();
    }

    /** 创建排班的标准请求体。 */
    private String scheduleBody(String date, String period) {
        return "{\"doctorId\":1,\"departmentId\":2,\"scheduleDate\":\"" + date
                + "\",\"timePeriod\":\"" + period + "\",\"totalSlots\":20}";
    }

    // 1. 创建排班成功
    @Test
    void createSchedule_success() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "MORNING")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.timePeriod").value("MORNING"))
                .andExpect(jsonPath("$.data.totalSlots").value(20))
                .andExpect(jsonPath("$.data.remainingSlots").value(20))
                .andExpect(jsonPath("$.data.startTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.endTime").value("12:00:00"))
                .andExpect(jsonPath("$.data.doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.departmentName").value("呼吸内科"));
    }

    // 2. 创建排班冲突：同医生同日同班次 → 409
    @Test
    void createSchedule_conflict_returns409() throws Exception {
        String token = adminToken();
        String date = tomorrow();
        // 先创建一条
        mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(date, "AFTERNOON")))
                .andExpect(jsonPath("$.code").value(200));
        // 再创建同医生同日同班次
        mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(date, "AFTERNOON")))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该医生在此日期此时段已有排班"));
    }

    // 3. 创建排班日期超窗口 → 400
    @Test
    void createSchedule_dateOutOfRange_returns400() throws Exception {
        String token = adminToken();
        String farDate = LocalDate.now().plusDays(15).toString();
        mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(farDate, "MORNING")))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("排班日期最多提前14天"));
    }

    // 4. 创建排班无效班次 → 400
    @Test
    void createSchedule_invalidPeriod_returns400() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "NIGHT")))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无效班次：NIGHT，可选 MORNING/AFTERNOON/EVENING"));
    }

    // 5. 修改排班成功
    @Test
    void updateSchedule_success() throws Exception {
        String token = adminToken();
        // 先创建
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "EVENING")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 修改 totalSlots
        String updateBody = "{\"doctorId\":1,\"departmentId\":2,\"scheduleDate\":\""
                + tomorrow() + "\",\"timePeriod\":\"EVENING\",\"totalSlots\":30}";
        mockMvc.perform(put("/api/b/schedules/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalSlots").value(30))
                .andExpect(jsonPath("$.data.remainingSlots").value(30));
    }

    // 6. 修改 total 少于已用数 → 400
    @Test
    void updateSchedule_totalLessThanUsed_returns400() throws Exception {
        String token = adminToken();
        // 创建 totalSlots=20
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "MORNING")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 减号模拟已用：remaining 从 20 减到 15（已用 5）
        mockMvc.perform(patch("/api/b/schedules/" + id + "/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"delta\":-5}"))
                .andExpect(jsonPath("$.code").value(200));
        // 尝试改 total 为 3（< 已用 5）
        String updateBody = "{\"doctorId\":1,\"departmentId\":2,\"scheduleDate\":\""
                + tomorrow() + "\",\"timePeriod\":\"MORNING\",\"totalSlots\":3}";
        mockMvc.perform(put("/api/b/schedules/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(updateBody))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("号源总数不得少于已用数（5）"));
    }

    // 7. 停诊成功
    @Test
    void suspendSchedule_success() throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "MORNING")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(patch("/api/b/schedules/" + id + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    // 8. 恢复成功
    @Test
    void resumeSchedule_success() throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "AFTERNOON")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 先停诊
        mockMvc.perform(patch("/api/b/schedules/" + id + "/suspend")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
        // 再恢复
        mockMvc.perform(patch("/api/b/schedules/" + id + "/resume")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.remainingSlots").value(20));
    }

    // 9. 手动调整号源（加号，超过 total）
    @Test
    void adjustSlots_addBeyondTotal_success() throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "EVENING")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 加 5 号（remaining 20 → 25，超过 total 20）
        mockMvc.perform(patch("/api/b/schedules/" + id + "/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"delta\":5}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.remainingSlots").value(25))
                .andExpect(jsonPath("$.data.totalSlots").value(20));
    }

    // 10. 手动调整号源（减到负数）→ 400
    @Test
    void adjustSlots_negativeResult_returns400() throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "MORNING")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 减 25（remaining 20 → -5）
        mockMvc.perform(patch("/api/b/schedules/" + id + "/slots")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"delta\":-25}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("余量不足，当前剩余 20"));
    }

    // 11. 删除排班（无挂号）成功
    @Test
    void deleteSchedule_noRegistration_success() throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "AFTERNOON")))
                .andReturn();
        long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        mockMvc.perform(delete("/api/b/schedules/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
        // 验证已删
        mockMvc.perform(get("/api/b/schedules/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));
    }

    // 12. 分页查询
    @Test
    void pageSchedules_returnsPageResponse() throws Exception {
        String token = adminToken();
        // 先创建一条确保有数据
        mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(scheduleBody(tomorrow(), "EVENING")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/b/schedules")
                        .param("doctorId", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    // 13. 周复制
    @Test
    void copyWeek_success() throws Exception {
        String token = adminToken();
        // 找本周一和下周周一
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextMonday = thisMonday.plusDays(7);

        // 在本周创建一个排班（如果明天在本周内）
        LocalDate srcDate = thisMonday.plusDays(1); // 周二
        LocalDate maxDate = LocalDate.now().plusDays(14);
        if (!srcDate.isBefore(LocalDate.now()) && !srcDate.isAfter(maxDate)) {
            mockMvc.perform(post("/api/b/schedules")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content(scheduleBody(srcDate.toString(), "MORNING")))
                    .andExpect(jsonPath("$.code").value(200));
        }

        // 目标周必须在窗口内
        LocalDate tgtEnd = nextMonday.plusDays(6);
        if (tgtEnd.isAfter(maxDate)) {
            // 窗口不够，跳过复制验证（仅验证参数校验）
            mockMvc.perform(post("/api/b/schedules/copy-week")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"sourceWeekStart\":\"" + thisMonday + "\",\"targetWeekStart\":\"" + nextMonday + "\"}"))
                    .andExpect(jsonPath("$.code").value(400));
        } else {
            mockMvc.perform(post("/api/b/schedules/copy-week")
                            .header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"sourceWeekStart\":\"" + thisMonday + "\",\"targetWeekStart\":\"" + nextMonday + "\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.created").isNumber())
                    .andExpect(jsonPath("$.data.skipped").isNumber());
        }
    }

    // 14. DOCTOR 角色访问排班管理 → 403
    @Test
    void doctorAccessSchedule_returns403() throws Exception {
        String token = doctorToken();
        mockMvc.perform(get("/api/b/schedules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(403));
    }
}
