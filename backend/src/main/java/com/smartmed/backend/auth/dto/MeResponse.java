package com.smartmed.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** 当前用户信息（GET /api/b/auth/me，从 JWT claim 解析，零 DB 命中）。ADMIN 时 doctorId 省略。 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeResponse {

    private Long userId;
    private String username;
    private String role;
    /** 医生 ID，DOCTOR 才有，ADMIN 为 null。 */
    private Long doctorId;
    /** 首登改密标志（ADR-0005）。 */
    private boolean mustChangePassword;
}
