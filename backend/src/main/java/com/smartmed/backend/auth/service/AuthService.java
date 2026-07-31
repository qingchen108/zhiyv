package com.smartmed.backend.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartmed.backend.auth.dto.ChangePasswordRequest;
import com.smartmed.backend.auth.dto.DemoLoginResponse;
import com.smartmed.backend.auth.dto.LoginRequest;
import com.smartmed.backend.auth.dto.LoginResponse;
import com.smartmed.backend.auth.dto.MeResponse;
import com.smartmed.backend.auth.entity.Patient;
import com.smartmed.backend.auth.entity.SysUser;
import com.smartmed.backend.auth.mapper.PatientMapper;
import com.smartmed.backend.auth.mapper.SysUserMapper;
import com.smartmed.backend.common.BusinessException;
import com.smartmed.backend.security.JwtTokenProvider;
import com.smartmed.backend.security.RefreshTokenService;
import com.smartmed.backend.security.SecurityUtil;
import com.smartmed.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务：B 端登录 / refresh / logout / 改密，C 端 demo-login，当前用户。
 * <p>
 * ADR-0013：B 端 access(30min) + refresh(8h) 双 token，Redis 会话、轮换、重用检测、固定窗口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 预设演示患者 ID（CONTEXT §3：打开即登录，无需授权）。 */
    private static final long DEMO_PATIENT_ID = 1L;

    private final SysUserMapper sysUserMapper;
    private final PatientMapper patientMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;

    /** 创建会话并签发 access + refresh。供登录 / 续期复用。 */
    private LoginResponse issueTokens(SysUser user) {
        long now = System.currentTimeMillis();
        // 固定窗口（Q12）：绝对截止 = 登录时刻 + 8h，续期不延长
        long absoluteExp = now + tokenProvider.getBRefreshExpireSeconds() * 1000L;
        String rjti = refreshTokenService.createSession(
                user.getId(), user.getRole(), user.getDoctorId(),
                Boolean.TRUE.equals(user.getMustChangePassword()), absoluteExp);
        String accessToken = tokenProvider.issueAccessToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDoctorId(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                tokenProvider.newId(), rjti, absoluteExp, now);
        String refreshToken = tokenProvider.issueRefreshToken(user.getId(), rjti, now);
        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole())
                .doctorId(user.getDoctorId())
                .expiresIn(tokenProvider.getBAccessExpireSeconds())
                .refreshExpiresIn(tokenProvider.getBRefreshExpireSeconds())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }

    /**
     * B 端登录：手机号 + 密码 -> access + refresh（ADR-0013）。
     * 失败抛 BusinessException(401, "用户名或密码错误")，不区分手机号错/密码错防枚举。
     */
    public LoginResponse login(LoginRequest req) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, req.getPhone()));
        // 用户不存在 / 密码不匹配 / 账号禁用 均视为登录失败
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return issueTokens(user);
    }

    /**
     * 换发 access（refresh 换发）：
     * 校验 refresh token（签名 + typ=B_RT + Redis 会话 + 固定窗口）-> 轮换 -> 签发新 access + 新 refresh。
     */
    public LoginResponse refresh(String refreshToken) {
        UserPrincipal rt = tokenProvider.parse(refreshToken);
        if (rt == null || !JwtTokenProvider.TYP_B_RT.equals(rt.getTyp()) || rt.getRjti() == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        RefreshTokenService.RotateResult rotated =
                refreshTokenService.rotate(rt.getUserId(), rt.getRjti(), System.currentTimeMillis());
        SysUser user = sysUserMapper.selectById(rt.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            // 账号被禁用/删除：吊销会话，拒绝续期
            refreshTokenService.revokeAllByUser(rt.getUserId());
            throw new BusinessException(401, "账号不可用，请重新登录");
        }
        long now = System.currentTimeMillis();
        String accessToken = tokenProvider.issueAccessToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDoctorId(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                tokenProvider.newId(), rotated.getNewRjti(), rotated.getAbsoluteExp(), now);
        String newRefreshToken = tokenProvider.issueRefreshToken(user.getId(), rotated.getNewRjti(), now);
        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(newRefreshToken)
                .role(user.getRole())
                .doctorId(user.getDoctorId())
                .expiresIn(tokenProvider.getBAccessExpireSeconds())
                .refreshExpiresIn(tokenProvider.getBRefreshExpireSeconds())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }

    /** 退出登录：吊销当前会话（Q13）。 */
    public void logout() {
        UserPrincipal p = SecurityUtil.current();
        refreshTokenService.revoke(p.getRefreshJti());
        log.info("用户退出登录: userId={}", p.getUserId());
    }

    /**
     * C 端 demo-login：无参 -> 预设 patient.id=1 的 JWT（typ=C，exp=7d）。
     * 查一次库返回 patientName，省前端二次查询。维持单 token（Q14：仅 B 端接 refresh）。
     */
    public DemoLoginResponse demoLogin() {
        Patient patient = patientMapper.selectById(DEMO_PATIENT_ID);
        if (patient == null) {
            throw new BusinessException(500, "演示患者未初始化，请检查种子数据");
        }
        String token = tokenProvider.issueCToken(patient.getId());
        return DemoLoginResponse.builder()
                .token(token)
                .patientId(patient.getId())
                .patientName(patient.getName())
                .expiresIn(tokenProvider.getCExpireSeconds())
                .build();
    }

    /** 当前用户信息：从 JWT claim 解析，零 DB 命中。 */
    public MeResponse currentUser() {
        UserPrincipal p = SecurityUtil.current();
        return MeResponse.builder()
                .userId(p.getUserId())
                .username(p.getUsername())
                .role(p.getRole())
                .doctorId(p.getDoctorId())
                .mustChangePassword(p.isMustChangePassword())
                .build();
    }

    /**
     * 修改密码（ADR-0005 首登改密）：校验旧密码 -> BCrypt 新密码 -> 置 must_change_password=false
     * -> 吊销该用户全部会话（Q15，须用新密码重新登录）。
     * 旧密码错误抛 BusinessException(401, "旧密码错误")。
     */
    public void changePassword(ChangePasswordRequest req) {
        UserPrincipal p = SecurityUtil.current();
        SysUser user = sysUserMapper.selectById(p.getUserId());
        if (user == null || !passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        sysUserMapper.updateById(user);
        refreshTokenService.revokeAllByUser(p.getUserId());
        log.info("修改密码成功，吊销全部会话: userId={}", p.getUserId());
    }
}
