package com.smartmed.backend.patient.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 患者档案更新请求（C 端，07 ticket）。
 * <p>
 * 仅允许患者修改部分字段：name、gender、birthDate、allergyHistory。
 * phone 不可改（需通过其他途径修改）。
 */
@Data
public class PatientProfileUpdateRequest {

    @Size(max = 64, message = "姓名不超过64个字符")
    private String name;

    private String gender;

    private LocalDate birthDate;

    @Size(max = 256, message = "过敏史不超过256个字符")
    private String allergyHistory;
}