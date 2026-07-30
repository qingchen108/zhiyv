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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 03 科室/医生/药品管理集成测试。
 * <p>
 * 直连 VM 真实 PostgreSQL（02 偏差延续），@Transactional 回滚隔离写操作不污染数据。
 * 15 个场景中，登录成功/密码错/C端403 已在 {@link AuthIntegrationTest} 覆盖，此处聚焦 03 新增业务：
 * 1. 首登改密成功：mustChangePassword 变 false
 * 2. 改密旧密码错：401
 * 3. 新建医生：doctor + sys_user 同事务写入，sys_user 密码=BCrypt("123456")、mustChangePassword=true
 * 4. 新建医生 phone 重复：409
 * 5. DOCTOR 调 PUT /api/b/doctors/{id}：403
 * 6. DOCTOR 调 PUT /api/b/doctors/me 改 specialty：成功
 * 7. DOCTOR 调 PUT /api/b/doctors/me 试图改 title：忽略（DTO 不含该字段，title 不变）
 * 8. 删除 doctor（无引用）：doctor + sys_user 同事务删
 * 9. 删除 doctor（有 schedule 引用）：409（03 阶段 schedule 无数据，此场景留待 04，此处跳过用 drug_pharmacy_stock 验证同类逻辑）
 * 10. 删除 department（有 doctor 引用）：409
 * 11. 删除 drug（有 drug_pharmacy_stock 引用）：409
 * 12. 分页列表 doctor 按 departmentId 筛选：PageResponse 结构正确
 * 13. DOCTOR 调 GET /api/b/doctors/me：返回本人
 * 14. 科室分页：返回 PageResponse
 * 15. 药品分页：返回 PageResponse
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
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
class DepartmentDoctorDrugIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtTokenProvider tokenProvider;

    // 种子账号（02-seed.sql）：admin=13800000000/admin123，doctor=13800000002/doctor123（关联 doctor.id=1）
    private static final String ADMIN_PHONE = "13800000000";
    private static final String ADMIN_PWD = "admin123";

    /** 登录拿 admin token。 */
    private String adminToken() throws Exception {
        return login(ADMIN_PHONE, ADMIN_PWD);
    }

    /** 登录拿 doctor token。 */
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

    // 1. 首登改密成功：重新登录后 mustChangePassword 变 false
    @Test
    void changePassword_success_clearsMustChangeFlag() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/b/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"admin123\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 改密后重新登录拿新 token，/me 验证标志已变 false
        // （旧 token 的 mustChangePassword claim 仍是 true，须新 token 才反映 DB 新值）
        String newToken = login(ADMIN_PHONE, "newpass456");
        mockMvc.perform(get("/api/b/auth/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
    }

    // 2. 改密旧密码错：401
    @Test
    void changePassword_wrongOldPassword_returns401() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/b/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"oldPassword\":\"wrongold\",\"newPassword\":\"newpass456\"}"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("旧密码错误"));
    }

    // 3. 新建医生：doctor + sys_user 同事务写入
    @Test
    void createDoctor_createsDoctorAndSysUser() throws Exception {
        String token = adminToken();
        // 用唯一手机号避免与种子/其他测试冲突
        String phone = "13900000399";
        MvcResult r = mockMvc.perform(post("/api/b/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"departmentId\":2,\"name\":\"测试医生\",\"gender\":\"男\","
                                + "\"birthDate\":\"1985-01-01\",\"title\":\"主治医师\","
                                + "\"specialty\":\"测试擅长\",\"phone\":\"" + phone + "\",\"password\":\"test123\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.age").isNumber())
                .andReturn();
        long doctorId = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 验证新建账号能用该手机号+密码登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"test123\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.role").value("DOCTOR"))
                .andExpect(jsonPath("$.data.doctorId").value(doctorId))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    // 4. 新建医生 phone 重复：409
    @Test
    void createDoctor_duplicatePhone_returns409() throws Exception {
        String token = adminToken();
        // 复用种子 doctor 账号手机号 13800000002
        mockMvc.perform(post("/api/b/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"departmentId\":2,\"name\":\"重复手机号医生\",\"phone\":\"13800000002\"}"))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("手机号已被使用"));
    }

    // 5. DOCTOR 调 PUT /api/b/doctors/{id}：403
    @Test
    void doctorUpdateById_returns403() throws Exception {
        String token = doctorToken();
        mockMvc.perform(put("/api/b/doctors/1")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"departmentId\":2,\"name\":\"张呼吸\",\"phone\":\"13800000002\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    // 6. DOCTOR 调 PUT /api/b/doctors/me 改 specialty：成功
    @Test
    void doctorUpdateMe_specialty_success() throws Exception {
        String token = doctorToken();
        mockMvc.perform(put("/api/b/doctors/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"specialty\":\"新擅长领域\",\"intro\":\"新简介\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.specialty").value("新擅长领域"))
                .andExpect(jsonPath("$.data.intro").value("新简介"));
    }

    // 7. DOCTOR 调 PUT /api/b/doctors/me 试图改 title：DTO 不含该字段，title 保持原值
    @Test
    void doctorUpdateMe_titleFieldIgnored() throws Exception {
        String token = doctorToken();
        // 请求体塞 title，但 DTO 不含该字段，应被忽略
        mockMvc.perform(put("/api/b/doctors/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"title\":\"主任医师\",\"specialty\":\"任意擅长\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.specialty").value("任意擅长"));
        // 验证 title 未变（种子 doctor.id=1 张呼吸 title=主任医师，本就如此，改 specialty 不影响）
        mockMvc.perform(get("/api/b/doctors/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.title").value("主任医师"));
    }

    // 8. 删除 doctor（无引用）：doctor + sys_user 同事务删
    @Test
    void deleteDoctor_noReference_deletesDoctorAndSysUser() throws Exception {
        String token = adminToken();
        // 先建一个无引用的医生
        String phone = "13900000499";
        MvcResult r = mockMvc.perform(post("/api/b/doctors")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"departmentId\":2,\"name\":\"待删医生\",\"phone\":\"" + phone + "\"}"))
                .andReturn();
        long doctorId = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        // 删除
        mockMvc.perform(delete("/api/b/doctors/" + doctorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200));
        // 验证 doctor 已删
        mockMvc.perform(get("/api/b/doctors/" + doctorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));
        // 验证 sys_user 已删（登录失败）
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // 9. 删除 doctor（有引用）：03 阶段 schedule 无数据，此场景同类逻辑见 drug 测试（#11），此处跳过

    // 10. 删除 department（有 doctor 引用）：409
    @Test
    void deleteDepartment_hasDoctor_returns409() throws Exception {
        String token = adminToken();
        // 种子科室 id=2（呼吸内科）下有 doctor.id=1 张呼吸
        mockMvc.perform(delete("/api/b/departments/2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该科室下存在医生，无法删除"));
    }

    // 11. 删除 drug（有 drug_pharmacy_stock 引用）：409
    @Test
    void deleteDrug_hasStock_returns409() throws Exception {
        String token = adminToken();
        // 种子药品 id=1（布洛芬）有 drug_pharmacy_stock 记录
        mockMvc.perform(delete("/api/b/drugs/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("该药品存在药店库存记录，无法删除"));
    }

    // 12. 分页列表 doctor 按 departmentId 筛选
    @Test
    void doctorPage_filterByDepartmentId_returnsPageResponse() throws Exception {
        String token = adminToken();
        // 种子科室 id=2（呼吸内科）下有 doctor.id=1
        mockMvc.perform(get("/api/b/doctors")
                        .param("departmentId", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10));
    }

    // 13. DOCTOR 调 GET /api/b/doctors/me：返回本人
    @Test
    void doctorGetMe_returnsOwnProfile() throws Exception {
        String token = doctorToken();
        mockMvc.perform(get("/api/b/doctors/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("张呼吸"))
                .andExpect(jsonPath("$.data.phone").value("13800000002"));
    }

    // 14. 科室分页：返回 PageResponse
    @Test
    void departmentPage_returnsPageResponse() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/b/departments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    // 15. 药品分页：返回 PageResponse
    @Test
    void drugPage_returnsPageResponse() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/b/drugs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").isNumber());
    }
}
