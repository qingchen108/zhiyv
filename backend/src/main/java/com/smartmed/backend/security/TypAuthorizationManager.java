package com.smartmed.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 按 typ + 路径前缀分权的授权管理器（ADR-0003）。
 * <p>
 * <ul>
 *   <li>{@code /api/b/**}：需 typ=B</li>
 *   <li>{@code /api/c/**}：需 typ=C</li>
 * </ul>
 * typ 不匹配返回 403（由 GlobalExceptionHandler 捕获 AccessDeniedException 统一处理）。
 * 角色粒度（B 端 ADMIN/DOCTOR）由 {@code @PreAuthorize} 在方法层补充。
 */
@Component
public class TypAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI();
        Authentication auth = authentication.get();

        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            // 未认证：交由 SecurityFilterChain 的其余决策（未受保护端点放行，受保护的已在过滤器写 401）
            return new AuthorizationDecision(false);
        }

        String typ = principal.getTyp();
        boolean allowed = false;
        if (path.startsWith("/api/b/")) {
            // typ=B + role∈{ADMIN,DOCTOR}（role 由 sys_user CHECK 约束保证取值，此处校验非空）
            allowed = JwtTokenProvider.TYP_B.equals(typ) && principal.getRole() != null;
        } else if (path.startsWith("/api/c/")) {
            allowed = JwtTokenProvider.TYP_C.equals(typ);
        } else {
            // 非前缀受控路径（公开或 agent）由 SecurityConfig matchers 决策，此处不拦截
            allowed = true;
        }
        return new AuthorizationDecision(allowed);
    }
}
