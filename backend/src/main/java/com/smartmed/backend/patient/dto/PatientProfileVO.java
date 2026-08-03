package com.smartmed.backend.patient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * 患者档案 VO（C 端，07 ticket）。
 * <p>
 * 含派生的 age 字段，phone 脱敏展示（中间四位 ****）。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientProfileVO {

    private Long id;
    private String name;
    /** 手机号（C 端脱敏展示，如 138****0001）。 */
    private String phone;
    private String gender;
    private LocalDate birthDate;
    /** 派生的年龄（由 birthDate 计算，不存 DB）。 */
    private Integer age;
    private String allergyHistory;
}