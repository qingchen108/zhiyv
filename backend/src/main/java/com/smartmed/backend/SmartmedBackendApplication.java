package com.smartmed.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智愈 SmartMed 后端启动类。
 * <p>
 * 统一 BFF：业务 API + Agent 网关 + 号源并发控制（见 CONTEXT §2/§7/§8）。
 * 基包 {@code com.smartmed.backend}（ADR-0003）。
 */
@SpringBootApplication
@MapperScan("com.smartmed.backend.**.mapper")
@EnableAsync
public class SmartmedBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartmedBackendApplication.class, args);
    }
}
