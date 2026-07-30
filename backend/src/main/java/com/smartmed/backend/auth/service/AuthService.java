package com.smartmed.backend.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.smartmed.backend.security.SecurityUtil;
import com.smartmed.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 鉴权服务：B 端登录、C 端 demo-login、当前用户。
 * <p>
 * demo-login 固定按预设 {@code patient.id=1} 签发（CONTEXT §3）。
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

    /**
     * B 端登录：账号密码 -> JWT（typ=B，exp=12h）。
     * 失败抛 BusinessException(401, "用户名或密码错误")，不区分用户名错/密码错防枚举。
     */
    public LoginResponse login(LoginRequest req) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        // 用户不存在 / 密码不匹配 / 账号禁用 均视为登录失败
        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = tokenProvider.issueBToken(user.getId(), user.getUsername(), user.getRole(), user.getDoctorId());
        return LoginResponse.builder()
                .token(token)
                .role(user.getRole())
                .doctorId(user.getDoctorId())
                .expiresIn(tokenProvider.getBExpireSeconds())
                .build();
    }

    /**
     * C 端 demo-login：无参 -> 预设 patient.id=1 的 JWT（typ=C，exp=7d）。
     * 查一次库返回 patientName，省前端二次查询。
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
                .build();
    }
}
