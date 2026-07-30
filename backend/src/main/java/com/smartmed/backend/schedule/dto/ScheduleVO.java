package com.smartmed.backend.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 排班视图对象（响应）。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleVO {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private Long departmentId;
    private String departmentName;
    private LocalDate scheduleDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String timePeriod;
    private Integer totalSlots;
    private Integer remainingSlots;
    private String status;
}
