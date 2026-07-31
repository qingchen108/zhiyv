package com.smartmed.backend.consultation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 问诊视图对象（待接诊列表 + 问诊详情共用）。
 * <p>
 * preDiagnosisBrief 为摘要截取前 80 字（列表用），详情用 preDiagnosis 全文。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsultationVO {

    private Long id;
    private Long registrationId;
    private Long patientId;
    private Long doctorId;
    private String doctorName;
    private String departmentName;
    /** 挂号单号。 */
    private String regNo;
    /** 排班日期。 */
    private LocalDate scheduleDate;
    /** 班次：MORNING/AFTERNOON/EVENING。 */
    private String timePeriod;
    /** 实际就诊人姓名。 */
    private String visitorName;
    private String visitorGender;
    /** 派生年龄。 */
    private Integer visitorAge;
    /** 预问诊摘要全文（详情页用，06 阶段为 null）。 */
    private String preDiagnosis;
    /** 预问诊摘要截取前 80 字（列表用，null 时前端显示"暂无"）。 */
    private String preDiagnosisBrief;
    /** 医生诊断（问诊级）。 */
    private String diagnosis;
    /** WAITING/IN_PROGRESS/COMPLETED。 */
    private String status;
    private OffsetDateTime createdAt;
}
