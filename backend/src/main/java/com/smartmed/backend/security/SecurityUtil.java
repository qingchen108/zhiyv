package com.smartmed.backend.security;

import com.smartmed.backend.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：从 SecurityContext 取当前 {@link UserPrincipal}。
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /** 取当前认证主体，未登录抛业务异常（401）。 */
    public static UserPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return principal;
    }
}
