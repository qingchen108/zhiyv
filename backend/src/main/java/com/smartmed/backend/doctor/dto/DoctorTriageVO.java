package com.smartmed.backend.doctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 导诊场景医生推荐视图对象（ticket 11）。
 * <p>
 * 包含医生基本信息 + 可约号源列表，按好评率+余量排序。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorTriageVO {

    private Long id;
    private Long departmentId;
    private String departmentName;
    private String name;
    private String title;
    private String specialty;
    private String avatarUrl;
    private String intro;
    private BigDecimal goodRate;

    /** 可约号源列表（schedule 摘要）。 */
    private List<AvailableSlot> availableSlots;

    /** 号源摘要。 */
    @Data
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AvailableSlot {
        private Long scheduleId;
        private String date;
        private String timePeriod;
        private String startTime;
        private String endTime;
        private Integer remainingSlots;
    }
}