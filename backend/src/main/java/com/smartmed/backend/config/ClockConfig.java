package com.smartmed.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时钟 Bean 配置（ADR-0018 修订）。
 * <p>
 * 生产环境返回系统默认时区时钟，行为与 {@code LocalDate.now()} / {@code LocalTime.now()} 完全一致。
 * 引入此 Bean 是为了让业务 Service 通过构造注入 {@link Clock}，使时间来源可测：
 * 集成测试用 {@code Clock.fixed} 固定在上午 10:00，消除"班次已结束"校验的时间窗口敏感
 * （08b：原 todayPeriod() 在 21:00 后选 EVENING 已结束，导致挂号草稿 400）。
 * <p>
 * 未接入 Clock 的类（年龄派生 / AutoFill / JWT / @Scheduled SQL）仍用各自静态 now()，
 * 不受本 Bean 影响。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
