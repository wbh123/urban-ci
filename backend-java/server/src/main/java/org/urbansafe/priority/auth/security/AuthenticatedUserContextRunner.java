package org.urbansafe.priority.auth.security;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.service.AuthService;

/**
 * 在后台任务线程中按数据库当前状态恢复短生命周期用户认证上下文。
 *
 * <p>只信任任务持久化的 userId；用户名、账号状态与角色均在真正执行前重新从数据库读取，
 * 避免任务重试或进程重启后继续使用已经撤销的角色快照。任务结束后恢复原上下文，防止线程复用串号。
 */
@Component
public class AuthenticatedUserContextRunner {

    private final AuthService authService;

    public AuthenticatedUserContextRunner(AuthService authService) {
        this.authService = authService;
    }

    public <T> T runAs(UUID userId, Supplier<T> action) {
        if (userId == null) {
            throw new AccessDeniedException("ASYNC_TASK_AUTHENTICATED_USER_REQUIRED");
        }

        CurrentUserResult user = authService.getCurrentUser(userId);
        List<String> roles = authService.getUserRoles(userId).stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        CurrentUser.UserInfo principal = new CurrentUser.UserInfo(
                user.id(), user.username(), roles);
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext temporary = SecurityContextHolder.createEmptyContext();
        temporary.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, authorities));
        SecurityContextHolder.setContext(temporary);

        try {
            return action.get();
        } finally {
            if (previous.getAuthentication() == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.setContext(previous);
            }
        }
    }
}
