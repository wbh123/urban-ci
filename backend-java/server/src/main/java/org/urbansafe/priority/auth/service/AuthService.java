package org.urbansafe.priority.auth.service;

import java.util.List;
import java.util.UUID;
import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.result.LoginResult;

public interface AuthService {

    /**
     * 校验用户名和密码，并在成功后签发访问令牌。
     *
     * @param username 登录用户名
     * @param password 登录明文密码
     * @return 与 OpenAPI DTO 解耦的内部登录结果
     */
    LoginResult login(String username, String password);

    /** 记录当前用户主动退出操作；第一阶段 JWT 无状态模式不维护服务端会话。 */
    void logout();

    /**
     * 查询当前有效用户的档案和角色详情。
     *
     * @param userId 当前认证用户唯一标识
     * @return 与 OpenAPI DTO 解耦的内部用户结果
     */
    CurrentUserResult getCurrentUser(UUID userId);

    /**
     * 查询用户拥有的角色编码，用于令牌签发和授权判断。
     *
     * @param userId 用户唯一标识
     * @return 不可变角色编码列表
     */
    List<String> getUserRoles(UUID userId);
}
