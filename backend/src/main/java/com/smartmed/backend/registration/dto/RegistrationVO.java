package com.smartmed.backend.registration.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 挂号记录视图（05 ticket，凭证 + 列表/详情共用）。
 */
@Data
@Builder
public class RegistrationVO {

    private Long id;
    private String regNo;
    private Long patientId;
    private Long familyMemberId;
    /** 实际就诊人姓名（本人取 patient.name，家人取 family_member.name） */
    private String visitorName;
    private Long scheduleId;
    private Long doctorId;
    private String doctorName;
    private String departmentName;
    private LocalDate scheduleDate;
    private String timePeriod;
    private String status;
    private OffsetDateTime createdAt;
}
