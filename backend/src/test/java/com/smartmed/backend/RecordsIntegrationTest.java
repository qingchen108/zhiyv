package com.smartmed.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartmed.backend.order.entity.DrugOrder;
import com.smartmed.backend.order.entity.MedicationReminder;
import com.smartmed.backend.order.mapper.DrugOrderMapper;
import com.smartmed.backend.order.mapper.MedicationReminderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 08 C 端记录查询集成测试。
 * <p>
 * 直连 VM PostgreSQL + Redis + Neo4j，@Transactional 回滚隔离。
 * 覆盖场景：
 * 1. 问诊记录列表（挂号自动建问诊后 C 端可见）
 * 2. 问诊详情
 * 3. 问诊对话记录（医生发消息后 C 端可见）
 * 4. 该问诊的处方列表
 * 5. 处方列表/详情（含开方医生姓名）
 * 6. 购药订单列表/详情（含药店名）
 * 7. 用药提醒列表（含药品名）
 * 8. 健康档案汇总（就诊/用药汇总）
 * 9. 按成员筛选（家人挂号 → familyMemberId 过滤）
 * 10. 跨患者成员 403
 * 11. 挂号列表按成员筛选
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration"
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.FixedClockConfig.class)
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
class RecordsIntegrationTest extends IntegrationTestBase {

    @Autowired
    private DrugOrderMapper drugOrderMapper;
    @Autowired
    private MedicationReminderMapper reminderMapper;

    /**
     * 固定班次：Clock 固定在 10:00，MORNING（08:00-12:00）永不结束，挂号不被拒。
     * （08b：原 todayPeriod() 在 21:00 后选 EVENING 已结束导致 400，现由固定 Clock 消除）
     */
    private String todayPeriod() {
        return "MORNING";
    }

    /** 创建今日排班（医生 1，科室 2）。 */
    private long createTodaySchedule(int totalSlots) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/b/schedules")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"doctorId\":1,\"departmentId\":2,\"scheduleDate\":\"" + today()
                                + "\",\"timePeriod\":\"" + todayPeriod() + "\",\"totalSlots\":" + totalSlots + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").get("id").asLong();
    }

    /** 创建草稿并返回 confirmToken（本人）。 */
    private String createDraftAndGetToken(long scheduleId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").get("confirmToken").asText();
    }

    /** 确认挂号（本人），返回 registrationId。 */
    private long confirmAndGetRegistrationId(long scheduleId) throws Exception {
        String confirmToken = createDraftAndGetToken(scheduleId);
        redisTemplate.delete("reg_ratelimit:1:self:" + scheduleId);
        MvcResult r = mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data").get("id").asLong();
    }

    /** 确认挂号后，从今日待接诊列表取 consultationId。 */
    private long confirmAndGetConsultationId(long scheduleId) throws Exception {
        confirmAndGetRegistrationId(scheduleId);
        MvcResult r = mockMvc.perform(get("/api/b/consultations/today")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data")
                .get("records").get(0).get("id").asLong();
    }

    /** 接诊。 */
    private void startConsultation(long consultationId) throws Exception {
        mockMvc.perform(patch("/api/b/consultations/" + consultationId + "/start")
                        .header("Authorization", "Bearer " + doctorToken()))
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 开方（医生，对乙酰氨基酚 id=2）。 */
    private long createPrescription(long consultationId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/b/prescriptions")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"consultationId\":" + consultationId + ",\"diagnosis\":\"上呼吸道感染\","
                                + "\"advice\":\"多饮水\","
                                + "\"items\":[{\"drugId\":2,\"usageMethod\":\"口服\",\"dosage\":\"0.5g\","
                                + "\"frequency\":\"每日2次\",\"remark\":\"饭后\"}]}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).path("data")
                .path("prescription").get("id").asLong();
    }

    // 1. 问诊记录列表：挂号自动建问诊后 C 端可见
    @Test
    void pageConsultations_afterRegistration_visible() throws Exception {
        long scheduleId = createTodaySchedule(10);
        confirmAndGetRegistrationId(scheduleId);

        mockMvc.perform(get("/api/c/records/consultations")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data.records[0].doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.records[0].visitorName").value("演示患者"))
                .andExpect(jsonPath("$.data.records[0].regNo").isNotEmpty())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    // 2. 问诊详情
    @Test
    void getConsultation_success() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);

        mockMvc.perform(get("/api/c/records/consultations/" + consultationId)
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(consultationId))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.departmentName").value("呼吸内科"));
    }

    // 3. 问诊对话：医生发消息后 C 端可见
    @Test
    void consultationMessages_visibleToPatient() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);
        mockMvc.perform(post("/api/b/consultations/" + consultationId + "/messages")
                        .header("Authorization", "Bearer " + doctorToken())
                        .contentType("application/json")
                        .content("{\"content\":\"您好，请描述症状\"}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/c/records/consultations/" + consultationId + "/messages")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].senderType").value("DOCTOR"))
                .andExpect(jsonPath("$.data[0].content").value("您好，请描述症状"));
    }

    // 4. 该问诊的处方列表（问诊详情页展示）
    @Test
    void consultationPrescriptions_visible() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);
        long prescriptionId = createPrescription(consultationId);

        mockMvc.perform(get("/api/c/records/consultations/" + consultationId + "/prescriptions")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(prescriptionId))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].items[0].drugName").value("对乙酰氨基酚"));
    }

    // 5. 处方列表 + 详情（含开方医生姓名）
    @Test
    void pageAndGetPrescription_success() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);
        long prescriptionId = createPrescription(consultationId);

        mockMvc.perform(get("/api/c/records/prescriptions")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(prescriptionId))
                .andExpect(jsonPath("$.data.records[0].doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.records[0].diagnosis").value("上呼吸道感染"))
                .andExpect(jsonPath("$.data.records[0].advice").value("多饮水"));

        mockMvc.perform(get("/api/c/records/prescriptions/" + prescriptionId)
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.items[0].drugName").value("对乙酰氨基酚"))
                .andExpect(jsonPath("$.data.items[0].frequency").value("每日2次"));
    }

    // 6. 购药订单列表 + 详情（含药店名）
    @Test
    void pageAndGetOrder_success() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);
        long prescriptionId = createPrescription(consultationId);

        // 暂无 C 端下单 API（ticket 14），直接插订单行模拟
        DrugOrder order = new DrugOrder();
        order.setPatientId(1L);
        order.setPrescriptionId(prescriptionId);
        order.setPharmacyId(1L);
        order.setTotalAmount(new BigDecimal("36.00"));
        order.setStatus("DELIVERING");
        order.setDeliveryInfo("预计 30 分钟内送达");
        drugOrderMapper.insert(order);

        mockMvc.perform(get("/api/c/records/orders")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value(order.getId()))
                .andExpect(jsonPath("$.data.records[0].pharmacyName").value("老百姓大药房(健康路店)"))
                .andExpect(jsonPath("$.data.records[0].status").value("DELIVERING"))
                .andExpect(jsonPath("$.data.records[0].totalAmount").value(36.0));

        mockMvc.perform(get("/api/c/records/orders/" + order.getId())
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pharmacyAddress").value("智愈市高新区健康路 88 号"))
                .andExpect(jsonPath("$.data.deliveryInfo").value("预计 30 分钟内送达"));
    }

    // 7. 用药提醒列表（含药品名）
    @Test
    void listReminders_success() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);
        long prescriptionId = createPrescription(consultationId);

        MedicationReminder reminder = new MedicationReminder();
        reminder.setPatientId(1L);
        reminder.setPrescriptionId(prescriptionId);
        reminder.setDrugId(2L);
        reminder.setNextRemindAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(8));
        reminder.setFrequency("每日2次");
        reminder.setDosage("0.5g");
        reminder.setStatus("ACTIVE");
        reminderMapper.insert(reminder);

        mockMvc.perform(get("/api/c/records/reminders")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].drugName").value("对乙酰氨基酚"))
                .andExpect(jsonPath("$.data[0].frequency").value("每日2次"))
                .andExpect(jsonPath("$.data[0].dosage").value("0.5g"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    // 8. 健康档案汇总（基本信息 + 就诊/用药汇总）
    @Test
    void healthProfile_aggregatesAll() throws Exception {
        long scheduleId = createTodaySchedule(10);
        long consultationId = confirmAndGetConsultationId(scheduleId);
        startConsultation(consultationId);
        long prescriptionId = createPrescription(consultationId);

        DrugOrder order = new DrugOrder();
        order.setPatientId(1L);
        order.setPrescriptionId(prescriptionId);
        order.setPharmacyId(1L);
        order.setTotalAmount(new BigDecimal("36.00"));
        order.setStatus("PENDING");
        drugOrderMapper.insert(order);

        mockMvc.perform(get("/api/c/records/health-profile")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.visitorName").value("演示患者"))
                .andExpect(jsonPath("$.data.allergyHistory").value("青霉素"))
                .andExpect(jsonPath("$.data.registrations[0].status").value("REGISTERED"))
                .andExpect(jsonPath("$.data.consultations[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.prescriptions[0].doctorName").value("张呼吸"))
                .andExpect(jsonPath("$.data.orders[0].pharmacyName").value("老百姓大药房(健康路店)"));
    }

    // 9. 按成员筛选：家人挂号后，familyMemberId 过滤问诊/挂号
    @Test
    void recordsFilteredByFamilyMember() throws Exception {
        // 添加家庭成员
        MvcResult addResult = mockMvc.perform(post("/api/c/patients/family-members")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"name\":\"测试家人\",\"relationship\":\"配偶\",\"gender\":\"女\",\"birthDate\":\"1992-08-15\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        long memberId = objectMapper.readTree(addResult.getResponse().getContentAsString())
                .path("data").get("id").asLong();

        // 帮家人挂号
        long scheduleId = createTodaySchedule(10);
        String visitorId = "fm:" + memberId;
        MvcResult draftResult = mockMvc.perform(post("/api/c/registrations/draft")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"familyMemberId\":" + memberId + "}"))
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String confirmToken = objectMapper.readTree(draftResult.getResponse().getContentAsString())
                .path("data").get("confirmToken").asText();
        redisTemplate.delete("reg_ratelimit:1:" + visitorId + ":" + scheduleId);
        mockMvc.perform(post("/api/c/registrations/confirm")
                        .header("Authorization", "Bearer " + cToken())
                        .contentType("application/json")
                        .content("{\"scheduleId\":" + scheduleId + ",\"familyMemberId\":" + memberId
                                + ",\"confirmToken\":\"" + confirmToken + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.visitorName").value("测试家人"));

        // 问诊列表按成员过滤：本人为空、家人有 1 条
        mockMvc.perform(get("/api/c/records/consultations")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/c/records/consultations")
                        .header("Authorization", "Bearer " + cToken())
                        .param("familyMemberId", String.valueOf(memberId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].visitorName").value("测试家人"));

        // 挂号列表按成员过滤
        mockMvc.perform(get("/api/c/registrations")
                        .header("Authorization", "Bearer " + cToken())
                        .param("familyMemberId", String.valueOf(memberId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].visitorName").value("测试家人"));

        // 健康档案按成员过滤
        mockMvc.perform(get("/api/c/records/health-profile")
                        .header("Authorization", "Bearer " + cToken())
                        .param("familyMemberId", String.valueOf(memberId)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.visitorName").value("测试家人"))
                .andExpect(jsonPath("$.data.registrations[0].status").value("REGISTERED"));
    }

    // 10. 跨患者成员 403（成员不属于当前患者）
    @Test
    void records_foreignFamilyMember_returns403() throws Exception {
        mockMvc.perform(get("/api/c/records/consultations")
                        .header("Authorization", "Bearer " + cToken())
                        .param("familyMemberId", "99999"))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权访问该成员的记录"));

        mockMvc.perform(get("/api/c/records/health-profile")
                        .header("Authorization", "Bearer " + cToken())
                        .param("familyMemberId", "99999"))
                .andExpect(jsonPath("$.code").value(403));
    }

    // 11. 越权详情：他人问诊 404（C 端归属校验）
    @Test
    void getConsultation_foreignPatient_returns404() throws Exception {
        mockMvc.perform(get("/api/c/records/consultations/99999")
                        .header("Authorization", "Bearer " + cToken()))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("问诊不存在"));
    }
}
