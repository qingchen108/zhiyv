package com.smartmed.backend.doctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 医生视图对象（响应）。
 * <p>
 * phone 来自 sys_user（单源镜像 JOIN，ADR-0005）；age 由 birthDate 派生计算。
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorVO {

    private Long id;
    private Long departmentId;
    private String name;
    private String gender;
    private LocalDate birthDate;
    /** 派生年龄（由 birthDate 计算，birthDate 为 null 则 age 为 null）。 */
    private Integer age;
    private String title;
    private String specialty;
    private String avatarUrl;
    private String intro;
    private BigDecimal goodRate;
    /** 登录手机号（来自 sys_user.phone，ADR-0005 单源镜像）。 */
    private String phone;
}
