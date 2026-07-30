package com.smartmed.backend.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 手动调整号源请求（ADR-0009：delta 正数加号、负数减号）。
 */
@Data
public class SlotAdjustRequest {

    @NotNull(message = "调整量不能为空")
    private Integer delta;
}
