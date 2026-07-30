package com.smartmed.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** B 端登录响应（03 ticket 接口契约）。ADMIN 时 doctorId 省略（非 null 占位）。 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    /** JWT token（typ=B）。 */
    private String token;
    /** 角色：ADMIN / DOCTOR。 */
    private String role;
    /** 医生 ID，DOCTOR 关联 doctor.id，ADMIN 为 null。 */
    private Long doctorId;
    /** 过期秒数（12h = 43200）。 */
    private long expiresIn;
    /** 首登改密标志：true 表示须改密后使用（ADR-0005）。 */
    private boolean mustChangePassword;
}
