package com.smartmed.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** B 端登录请求（ADR-0004：手机号 + 密码登录，非用户名）。 */
@Data
public class LoginRequest {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;
}
