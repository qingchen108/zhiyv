package com.smartmed.backend.registration.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 草稿创建响应（05 ticket，ticket 12 增强）。
 */
@Data
@Builder
public class RegistrationDraftResponse {

    /** 草稿 Redis key（供调试，前端不需要） */
    private String draftKey;
    /** 确认令牌（确认时回传） */
    private String confirmToken;
    /** 排班 ID */
    private Long scheduleId;
    /** 医生姓名 */
    private String doctorName;
    /** 科室名称 */
    private String departmentName;

    // === ticket 12 增强：卡片展示字段 ===

    /** 排班日期 */
    private LocalDate scheduleDate;
    /** 班次（MORNING/AFTERNOON/EVENING） */
    private String timePeriod;
    /** 时段范围（如 "08:00-12:00"） */
    private String timeRange;
    /** 就诊人姓名（"本人" 或家人姓名） */
    private String visitorName;
}
