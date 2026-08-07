package org.urbansafe.priority.audit.model;

import java.util.UUID;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.request.RequestContext;

/**
 * 当前请求的审计上下文，统一承载用户、请求编号和客户端地址。
 *
 * @param userId 当前认证用户；匿名请求允许为空
 * @param requestId 与响应头、响应体一致的请求编号
 * @param clientIp 经过请求过滤器规范化后的客户端地址
 */
public record AuditContext(UUID userId, String requestId, String clientIp) {

    /**
     * 从安全上下文和请求上下文捕获当前审计上下文。
     *
     * @return 当前线程的不可变审计上下文
     */
    public static AuditContext capture() {
        return new AuditContext(
                CurrentUser.getUserId(),
                RequestContext.getRequestId(),
                RequestContext.getClientIp());
    }
}
