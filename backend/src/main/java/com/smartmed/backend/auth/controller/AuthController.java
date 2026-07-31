package com.smartmed.backend.auth.controller;

import com.smartmed.backend.auth.dto.ChangePasswordRequest;
import com.smartmed.backend.auth.dto.DemoLoginResponse;
import com.smartmed.backend.auth.dto.LoginRequest;
import com.smartmed.backend.auth.dto.LoginResponse;
import com.smartmed.backend.auth.dto.MeResponse;
import com.smartmed.backend.auth.dto.RefreshRequest;
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
 * 鉴权接口（03 ticket 接口契约；07 增强 ADR-0013）。
 * <ul>
 *   <li>POST /api/auth/login - B 端手机号+密码登录（返回 access + refresh）</li>
 *   <li>POST /api/b/auth/refresh - B 端 refresh 换发 access（公开，需带 refresh token）</li>
 *   <li>POST /api/b/auth/logout - B 端退出（吊销当前会话）</li>
 *   <li>POST /api/c/auth/demo-login - C 端演示登录（无参）</li>
 *   <li>GET /api/b/auth/me - 当前用户信息（B 端）</li>
 *   <li>POST /api/b/auth/change-password - 修改密码（首登强制，改密吊销全部会话）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** B 端登录：手机号 + 密码 -> access + refresh（ADR-0013）。 */
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    /** B 端 refresh 换发：refresh token -> 新 access + 新 refresh（公开端点，Q14 仅 B 端）。 */
    @PostMapping("/b/auth/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.success(authService.refresh(req.getRefreshToken()));
    }

    /** B 端退出：吊销当前会话（Q13）。 */
    @PostMapping("/b/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
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

    /** 修改密码（ADR-0005 首登改密，改密吊销全部会话 Q15）。需 B 端 token。 */
    @PostMapping("/b/auth/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
        return Result.success();
    }
}
