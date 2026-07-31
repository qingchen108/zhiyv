package com.smartmed.backend.consultation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartmed.backend.prescription.dto.PrescriptionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 患者病历聚合视图（以实际就诊人为中心，CONTEXT §10）。
 * <p>
 * 跨医生可见：含其他医生接诊记录，仅"操作问诊流转"限本人。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicalRecordVO {

    /** 基本信息。 */
    private String visitorName;
    private String visitorGender;
    private Integer visitorAge;
    /** 过敏史原文（取 patient 或 patient_family_member）。 */
    private String allergyHistory;

    /** 历史挂号。 */
    private List<RegistrationSummary> registrations;
    /** 历史问诊（含诊断）。 */
    private List<ConsultationSummary> consultations;
    /** 历史处方（含明细）。 */
    private List<PrescriptionVO> prescriptions;

    @Data
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RegistrationSummary {
        private Long id;
        private String regNo;
        private String doctorName;
        private String departmentName;
        private LocalDate scheduleDate;
        private String timePeriod;
        private String status;
        private OffsetDateTime createdAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsultationSummary {
        private Long id;
        private String doctorName;
        private String diagnosis;
        private String status;
        private OffsetDateTime createdAt;
    }
}
