package com.smartmed.backend.health;

import com.smartmed.backend.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查接口（02 ticket）。
 * <p>
 * 只返进程存活（status + timestamp），不检中间件连通性，深度健康检查留作 P2。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }
}
