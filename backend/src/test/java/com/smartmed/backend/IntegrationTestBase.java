package com.smartmed.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmed.backend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试薄基类（ADR-0018 修订）。
 * <p>
 * 抽取四个集成测试（Registration / Schedule / DoctorWorkspace / Records）的公共字段与 helper，
 * 消除跨文件重复。内嵌 {@link FixedClockConfig} 把 {@link Clock} 固定在"运行当天 10:00"，
 * 使 {@code validateNotExpired}（LocalTime.now(clock)=10:00 &lt; MORNING.endTime=12:00）永不触发
 * "班次已结束"，任何时间跑测试都通过（08b 时间窗口修复）。
 * <p>
 * 子类各自保留 {@code @SpringBootTest} / {@code @TestPropertySource} / Neo4j 排除策略
 * （DoctorWorkspace 不排除 Neo4j，其余三个排除）。
 */
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JwtTokenProvider tokenProvider;
    @Autowired
    protected StringRedisTemplate redisTemplate;

    /** C 端演示患者 token（patient.id=1）。 */
    protected String cToken() {
        return tokenProvider.issueCToken(1L);
    }

    /** 管理员 token（admin / admin123）。 */
    protected String adminToken() throws Exception {
        return loginAs("13800000000", "admin123");
    }

    /** 演示医生 token（doctor.id=1，张呼吸，phone=13800000002）。 */
    protected String doctorToken() throws Exception {
        return loginAs("13800000002", "doctor123");
    }

    /** 手机号+密码登录，返回 B 端 JWT。 */
    protected String loginAs(String phone, String password) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        JsonNode data = objectMapper.readTree(r.getResponse().getContentAsString()).path("data");
        return data.get("token").asText();
    }

    /** 今日日期。 */
    protected String today() {
        return LocalDate.now().toString();
    }

    /** 明日日期。 */
    protected String tomorrow() {
        return LocalDate.now().plusDays(1).toString();
    }

    /**
     * 固定时钟配置：运行当天 10:00，系统默认时区。
     * <p>
     * 覆盖 {@link com.smartmed.backend.config.ClockConfig} 的系统时钟（@Primary 优先）。
     * 固定 10:00 后：LocalDate.now(clock)=运行当天（"今日待接诊"等断言不受影响），
     * LocalTime.now(clock)=10:00（MORNING 08:00-12:00 未结束，挂号不被拒）。
     */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            ZoneId zone = ZoneId.systemDefault();
            Instant fixed = LocalDate.now().atTime(LocalTime.of(10, 0)).atZone(zone).toInstant();
            return Clock.fixed(fixed, zone);
        }
    }
}
