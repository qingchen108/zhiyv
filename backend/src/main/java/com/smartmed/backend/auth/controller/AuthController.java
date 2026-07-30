package com.smartmed.backend.auth.controller;

import com.smartmed.backend.auth.dto.DemoLoginResponse;
import com.smartmed.backend.auth.dto.LoginRequest;
import com.smartmed.backend.auth.dto.LoginResponse;
import com.smartmed.backend.auth.dto.MeResponse;
import com.smartmed.backend.auth.service.AuthService;
import com.smartmed.backend.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口（02 ticket 接口契约）。
 * <ul>
 *   <li>POST /api/auth/login - B 端账号密码登录</li>
 *   <li>POST /api/c/auth/demo-login - C 端演示登录（无参）</li>
 *   <li>GET /api/b/auth/me - 当前用户信息（B 端）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** B 端登录：账号密码 -> JWT（typ=B）。 */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    /** C 端 demo-login：无参 -> 预设 patient.id=1 的 JWT（typ=C）。 */
    @PostMapping("/c/auth/demo-login")
    public Result<DemoLoginResponse> demoLogin() {
        return Result.success(authService.demoLogin());
    }

    /** 当前用户信息（从 JWT claim 解析，零 DB 命中）。需 B 端 token。 */
    @GetMapping("/b/auth/me")
    public Result<MeResponse> me() {
        return Result.success(authService.currentUser());
    }
}
