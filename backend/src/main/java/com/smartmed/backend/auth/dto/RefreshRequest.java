package com.smartmed.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** B 端 refresh 换发请求（ADR-0013）。 */
@Data
public class RefreshRequest {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
