package com.smartmed.backend.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * 认证主体，承载 JWT 解析后的用户上下文（ADR-0003）。
 * <p>
 * B 端：{@code typ=B}，携带 role（ADMIN/DOCTOR）与 doctorId（ADMIN 为 null）。
 * C 端：{@code typ=C}，携带 patientId。
 */
@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    /** token 端别：B 或 C。 */
    private final String typ;
    /** 用户 ID：B 端为 sys_user.id，C 端为 patient.id。 */
    private final Long userId;
    /** B 端角色：ADMIN / DOCTOR；C 端为 null。 */
    private final String role;
    /** B 端医生 ID：DOCTOR 关联 doctor.id，ADMIN 为 null。 */
    private final Long doctorId;
    /** C 端患者 ID；B 端为 null。 */
    private final Long patientId;
    /** 用户名（仅 B 端有）。 */
    private final String username;
    /** 首登改密标志（B 端，ADR-0005）。 */
    private final boolean mustChangePassword;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return null; // JWT 无状态，不持有密码
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
