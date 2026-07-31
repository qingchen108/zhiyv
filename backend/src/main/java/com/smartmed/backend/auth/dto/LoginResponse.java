package com.smartmed.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** B 端登录/刷新响应（03 ticket 接口契约；07 增强 access/refresh）。ADMIN 时 doctorId 省略。 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {

    /** access token（typ=B，30min）。 */
    private String token;
    /** refresh token（typ=B_RT，8h，Redis 存储，ADR-0013）。 */
    private String refreshToken;
    /** 角色：ADMIN / DOCTOR。 */
    private String role;
    /** 医生 ID，DOCTOR 关联 doctor.id，ADMIN 为 null。 */
    private Long doctorId;
    /** access 过期秒数（30min = 1800）。 */
    private long expiresIn;
    /** refresh 过期秒数（8h = 28800）。 */
    private long refreshExpiresIn;
    /** 首登改密标志：true 表示须改密后使用（ADR-0005）。 */
    private boolean mustChangePassword;
}
