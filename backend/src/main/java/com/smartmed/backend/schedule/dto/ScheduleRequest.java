package com.smartmed.backend.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 排班创建/修改请求（ADR-0009：全字段可编辑）。
 */
@Data
public class ScheduleRequest {

    @NotNull(message = "医生ID不能为空")
    private Long doctorId;

    @NotNull(message = "科室ID不能为空")
    private Long departmentId;

    @NotNull(message = "排班日期不能为空")
    private LocalDate scheduleDate;

    @NotBlank(message = "班次不能为空")
    private String timePeriod;

    @NotNull(message = "号源总数不能为空")
    @Min(value = 1, message = "号源总数至少为1")
    private Integer totalSlots;
}
