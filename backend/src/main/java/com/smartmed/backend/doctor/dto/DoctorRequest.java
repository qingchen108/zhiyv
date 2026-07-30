package com.smartmed.backend.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 医生新增/编辑请求（ADMIN，全字段）。
 * <p>
 * phone 为登录手机号（写 sys_user.phone），password 可选默认 "123456"（ADR-0005）。
 */
@Data
public class DoctorRequest {

    @NotNull(message = "科室不能为空")
    private Long departmentId;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String gender;
    private LocalDate birthDate;
    private String title;
    private String specialty;
    private String avatarUrl;
    private String intro;
    private BigDecimal goodRate;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 密码，可选，默认 "123456"（ADR-0005 首登改密）。仅新增生效，编辑忽略。 */
    private String password;
}
