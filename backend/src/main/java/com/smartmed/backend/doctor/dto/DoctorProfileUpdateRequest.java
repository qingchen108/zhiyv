package com.smartmed.backend.doctor.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 医生本人资料编辑请求（DOCTOR，仅 specialty/avatarUrl/intro 三字段，Q11）。
 * <p>
 * 不含 departmentId/name/gender/birthDate/title/goodRate/phone（DOCTOR 不可改）。
 */
@Data
public class DoctorProfileUpdateRequest {

    @Size(max = 256, message = "擅长领域不能超过 256 字")
    private String specialty;

    @Size(max = 512, message = "头像地址不能超过 512 字")
    private String avatarUrl;

    private String intro;
}
