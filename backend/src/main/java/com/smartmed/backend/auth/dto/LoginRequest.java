package com.smartmed.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** B 端登录请求。 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
