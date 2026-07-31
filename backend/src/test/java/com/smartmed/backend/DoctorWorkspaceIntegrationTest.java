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
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 06 医生工作台集成测试。
 * <p>
 * 直连 VM PostgreSQL + Redis + Neo4j，@Transactional 回滚隔离。
 * 覆盖 12 个场景（见 ticket 06 测试清单）。
 * <p>
 * 种子数据依赖：
 * - 演示患者 patient.id=1（过敏史"青霉素"），C 端 token
 * - 演示医生 doctor.id=1（张呼吸，呼吸内科 dept=2，sys_user.id=2，phone=13800000002），DOCTOR token
 * - 药品 id=4 阿莫西林（含青霉素过敏原），id=3 阿司匹林 + id=1 布洛芬（INTERACTS_WITH）
 */
@SpringBootTest
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
class DoctorWorkspaceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokenProvider;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** C 端演示患者 token（patient.id=1）。 */
    private String cToken() {
        return tokenProvider.issueCToken(1L);
    }

    /** 演示医生 token（doctor.id=1，张呼吸）。 */
    private String doctorToken() throws Exception {
        return login("13800000002", "doctor123");
    }

    /** 管理员 token。 */
    private String adminToken() throws Exception {
        return login("13800000000", "admin123");
    }

    private String login(String phone, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("token").asText();
    }

    /** 今日日期。 */
    private String today() {
        return LocalDate.now().toString();
    }

    /**
     * 选一个今日尚未结束的班次，避免挂号时"班次已结束"校验失败。
     * 17:00 后选 EVENING，12:00 后选 AFTERNOON，否则 MORNING。
     */
    private String todayPeriod() {
        LocalTime now = LocalTime.now();
        if (now.isAfter(LocalTime.of(17, 0))) {
            return "EVENING";
        }
        if (now.isAfter(LocalTime.of(12, 0))) {
            return "AFTERNOON";
        }
        return "MORNING";
    }

    /** 创建今日排班并返回 schedule ID（医生 1，科室 2）。 */
    private long createTodaySchedule(int totalSlots) throws Exception {
        String token = adminToken();
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"doctorId\":1,\"departmentId\":2,\"scheduleDate\":\"" + today()
                                + "\",\"timePeriod\":\"" + todayPeriod() + "\",\"totalSlots\":" + totalSlots + "}"))
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

    /** 确认挂号，返回 consultationId（挂号成功后自动创建问诊）。 */
    private long confirmAndGetConsultationId(long scheduleId) throws Exception {
        String confirmToken = createDraftAndGetToken(scheduleId);
        // 防刷 key 清理（同一测试内多次挂号同 schedule 可能触发）
        redisTemplate.delete("reg_ratelimit:1:self:" + scheduleId);
        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200));
        // 今日待接诊列表取问诊 ID
        MvcResult r = mockMvc.perform(get("/api/b/consultations/today")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("records").get(0).get("id").asLong();
    }

    /** 接诊（WAITING -> IN_PROGRESS）。 */
    private void startConsultation(long consultationId) throws Exception {
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/start")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200));
    }

    // 1. consultation 自动创建：确认挂号后 consultation 行存在（WAITING）
    @Test
    void confirmRegistration_autoCreatesConsultation() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);

        mockMvc.perform(get("/api/b/consultations/" + consultationId)
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(consultationId))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.preDiagnosis").doesNotExist())
                .andExpect(jsonPath("$.data.visitorName").value("演示患者"))
                .andExpect(jsonPath("$.data.doctorName").value("张呼吸"));
    }

    // 2. 今日待接诊列表：只返回当前医生今日 WAITING，分页正确
    @Test
    void todayWaiting_returnsOnlyCurrentDoctorWaiting() throws Exception {
        long scheduleId = createTodaySchedule(10);
        confirmAndGetConsultationId(scheduleId);

        mockMvc.perform(get("/api/b/consultations/today")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data.records[0].doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    // 3. 跨医生访问问诊详情：403
    @Test
    void getById_crossDoctor_returns403() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);

        // 新建第二个医生（李神经的科室 dept=1，但本测试只需另一个 DOCTOR 账号）
        String admin = adminToken();
        mockMvc.perform(post("/api/b/doctors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"departmentId\":1,\"name\":\"测试医生B\",\"gender\":\"男\","
                                + "\"birthDate\":\"1980-01-01\",\"title\":\"主治医师\","
                                + "\"phone\":\"13900000099\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(200));

        String doctorBToken = login("13900000099", "123456");

        mockMvc.perform(get("/api/b/consultations/" + consultationId)
                        .header("Authorization", "Bearer " + doctorBToken))
                .andExpect(jsonPath("$.code").value(403));
    }

    // 4. 接诊流转：WAITING->IN_PROGRESS 成功；complete->COMPLETED；registration 同步 VISITED
    @Test
    void statusFlow_completeSyncsRegistrationVisited() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);

        // 接诊
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/start")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 完成
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/complete")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 查挂号记录确认同步 VISITED（通过 C 端挂号详情查）
        MvcResult regList = mockMvc.perform(get("/api/c/registrations")
                        .header("Authorization", "Bearer " + cToken())
                        .param("status", "VISITED"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        JsonNode records = objectMapper.readTree(regList.getResponse().getContentAsString())
                .path("data").path("records");
        boolean hasVisited = false;
        for (JsonNode r : records) {
            if ("VISITED".equals(r.get("status").asText())) {
                hasVisited = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(hasVisited, "完成问诊后挂号应同步 VISITED");
    }

    // 5. 状态回退：COMPLETED->start 400；IN_PROGRESS->start 400
    @Test
    void statusRevert_returns400() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);

        // IN_PROGRESS -> start 400
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/start")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(400));

        // 完成
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/complete")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200));

        // COMPLETED -> start 400
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/start")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(400));

        // COMPLETED -> complete 400
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/complete")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(400));
    }

    // 6. 发消息：DOCTOR 发成功；WAITING/COMPLETED 时发 400
    @Test
    void sendMessage_statusGuard() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);

        // WAITING 发消息 400
        mockMvc.perform(post("/api/b/consultations/" + consultationId + "/messages")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"content\":\"你好\"}"))
                .andExpect(jsonPath("$.code").value(400));

        // 接诊后发消息成功
        startConsultation(consultationId);
        mockMvc.perform(post("/api/b/consultations/" + consultationId + "/messages")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"content\":\"你好，请描述症状\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.senderType").value("DOCTOR"))
                .andExpect(jsonPath("$.data.content").value("你好，请描述症状"));

        // 完成后发消息 400
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/complete")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/api/b/consultations/" + consultationId + "/messages")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"content\":\"再见\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // 7. 开方：IN_PROGRESS 时成功，返回 warnings
    @Test
    void createPrescription_inProgress_success() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);

        mockMvc.perform(post("/api/b/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"consultationId\":" + consultationId + ",\"diagnosis\":\"上呼吸道感染\","
                                + "\"advice\":\"多饮水\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"0.5g\","
                                + "\"frequency\":\"每日2次\",\"remark\":\"饭后\"}]}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.prescription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.prescription.items[0].drugId").value(2))
                .andExpect(jsonPath("$.data.warnings").isArray());
    }

    // 8. 开方冲突：处方含过敏药（阿莫西林 id=4 含青霉素，患者过敏史"青霉素"）-> warnings 含 ALLERGY；force=true 仍保存 ACTIVE
    @Test
    void createPrescription_allergyConflict_returnsWarningAndSavesOnForce() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);

        // 第一次：含过敏药，不强制 -> 仍保存（不阻断），返回 ALLERGY 警告
        mockMvc.perform(post("/api/b/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"consultationId\":" + consultationId + ",\"diagnosis\":\"细菌感染\","
                                + "\"items\":[{\"drugId\":4,\"usageMethod\":\"口服\",\"dosage\":\"0.25g\","
                                + "\"frequency\":\"每日3次\"}],\"force\":true}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.prescription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.warnings[0].type").value("ALLERGY"));
    }

    // 9. 开方：WAITING 时 400
    @Test
    void createPrescription_waiting_returns400() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        // 不接诊，保持 WAITING

        mockMvc.perform(post("/api/b/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"consultationId\":" + consultationId + ",\"diagnosis\":\"测试\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"0.5g\","
                                + "\"frequency\":\"每日2次\"}]}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("待接诊问诊不可开方"));
    }

    // 10. 处方模板 CRUD：本人增删改查；跨医生 403
    @Test
    void templateCrud_selfOk_crossDoctorForbidden() throws Exception {
        String doctor = doctorToken();

        // 新建
        MvcResult r = mockMvc.perform(post("/api/b/prescription-templates")
                        .header("Authorization", "Bearer " + doctor)
                        .contentType("application/json")
                        .content("{\"name\":\"感冒常用药\",\"applicableDiagnosis\":\"上呼吸道感染\","
                                + "\"advice\":\"多休息\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"0.5g\","
                                + "\"frequency\":\"每日2次\"}]}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("感冒常用药"))
                .andReturn();
        long templateId = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").get("id").asLong();

        // 查列表
        mockMvc.perform(get("/api/b/prescription-templates")
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("感冒常用药"));

        // 编辑
        mockMvc.perform(put("/api/b/prescription-templates/" + templateId)
                        .header("Authorization", "Bearer " + doctor)
                        .contentType("application/json")
                        .content("{\"name\":\"感冒常用药V2\",\"applicableDiagnosis\":\"感冒\","
                                + "\"advice\":\"多喝水\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"0.5g\","
                                + "\"frequency\":\"每日3次\"}]}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("感冒常用药V2"));

        // 跨医生访问（建第二个医生）
        String admin = adminToken();
        mockMvc.perform(post("/api/b/doctors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType("application/json")
                        .content("{\"departmentId\":1,\"name\":\"测试医生C\",\"gender\":\"女\","
                                + "\"birthDate\":\"1985-01-01\",\"title\":\"主治医师\","
                                + "\"phone\":\"13900000098\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(200));
        String doctorC = login("13900000098", "123456");

        // 跨医生编辑 403
        mockMvc.perform(put("/api/b/prescription-templates/" + templateId)
                        .header("Authorization", "Bearer " + doctorC)
                        .contentType("application/json")
                        .content("{\"name\":\"篡改\",\"applicableDiagnosis\":\"x\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"1g\","
                                + "\"frequency\":\"每日1次\"}]}"))
                .andExpect(jsonPath("$.code").value(403));

        // 删除（本人）
        mockMvc.perform(delete("/api/b/prescription-templates/" + templateId)
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(jsonPath("$.code").value(200));
    }

    // 11. 病历聚合：返回挂号+问诊+处方+过敏史，按实际就诊人
    @Test
    void medicalRecord_aggregatesByVisitor() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);

        // 保存诊断 + 开方
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/diagnosis")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"diagnosis\":\"急性上呼吸道感染\"}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/api/b/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"consultationId\":" + consultationId + ",\"diagnosis\":\"上感\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"0.5g\","
                                + "\"frequency\":\"每日2次\"}]}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/b/consultations/" + consultationId + "/medical-record")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.visitorName").value("演示患者"))
                .andExpect(jsonPath("$.data.allergyHistory").value("青霉素"))
                .andExpect(jsonPath("$.data.registrations[0].regNo").isNotEmpty())
                .andExpect(jsonPath("$.data.consultations[0].diagnosis").value("急性上呼吸道感染"))
                .andExpect(jsonPath("$.data.prescriptions[0].items[0].drugId").value(2));
    }

    // 12. Neo4j 检测正常：含相互作用药品（阿司匹林 id=3 + 布洛芬 id=1 INTERACTS_WITH）-> warnings 含 INTERACTION
    @Test
    void createPrescription_drugInteraction_returnsWarning() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);

        mockMvc.perform(post("/api/b/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"consultationId\":" + consultationId + ",\"diagnosis\":\"疼痛\","
                                + "\"items\":["
                                + "{\"drugId\":3,\"usageMethod\":\"口服\",\"dosage\":\"0.1g\",\"frequency\":\"每日1次\"},"
                                + "{\"drugId\":1,\"usageMethod\":\"口服\",\"dosage\":\"0.3g\",\"frequency\":\"每日2次\"}"
                                + "]}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.prescription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.warnings[?(@.type=='INTERACTION')]").exists());
    }
}
