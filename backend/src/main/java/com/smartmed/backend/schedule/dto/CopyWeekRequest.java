package com.smartmed.backend.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 周复制请求（ADR-0009：周以周一为起点）。
 */
@Data
public class CopyWeekRequest {

    @NotNull(message = "源周起始日不能为空")
    private LocalDate sourceWeekStart;

    @NotNull(message = "目标周起始日不能为空")
    private LocalDate targetWeekStart;
}
