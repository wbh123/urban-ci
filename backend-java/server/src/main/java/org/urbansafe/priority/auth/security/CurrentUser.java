package org.urbansafe.priority.auth.security;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring SecurityContext 读取当前用户的轻量工具类。
 *
 * <p>该实现不再维护额外 ThreadLocal，避免自定义 JWT 过滤器与安全上下文生命周期不一致。
 */
public final class CurrentUser {

    /** 工具类禁止实例化。 */
    private CurrentUser() {
    }

    /**
     * 获取当前认证用户；未认证时返回只读匿名对象。
     *
     * @return 当前用户信息
     */
    public static UserInfo get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return UserInfo.ANONYMOUS;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UserInfo userInfo ? userInfo : UserInfo.ANONYMOUS;
    }

    /** @return 当前用户 UUID，未认证时为 null */
    public static UUID getUserId() {
        return get().userId();
    }

    /** @return 当前用户名，未认证时为空字符串 */
    public static String getUsername() {
        return get().username();
    }

    /** @return 当前角色编码列表，不包含 ROLE_ 前缀 */
    public static List<String> getRoles() {
        return get().roles();
    }

    /** @return 安全上下文是否包含城安智序用户主体 */
    public static boolean isAuthenticated() {
        return !UserInfo.ANONYMOUS.equals(get());
    }

    /**
     * 清理当前 Spring Security 上下文，主要供测试和显式退出流程使用。
     */
    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 安全上下文中保存的最小用户信息。
     *
     * @param userId 用户 UUID
     * @param username 用户名
     * @param roles 角色编码列表
     */
    public record UserInfo(UUID userId, String username, List<String> roles) {

        /** 未认证请求使用的不可变匿名主体。 */
        public static final UserInfo ANONYMOUS = new UserInfo(null, "", Collections.emptyList());
    }
}
