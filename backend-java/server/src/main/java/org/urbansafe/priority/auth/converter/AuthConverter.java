package org.urbansafe.priority.auth.converter;

import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.result.CurrentUserRoleResult;
import org.urbansafe.priority.auth.result.LoginResult;
import org.urbansafe.priority.model.dto.CurrentUserResponse;
import org.urbansafe.priority.model.dto.CurrentUserRole;
import org.urbansafe.priority.model.dto.LoginResponse;
import org.urbansafe.priority.model.dto.LoginUser;

/**
 * 认证内部结果到 OpenAPI 生成 DTO 的唯一转换入口。
 *
 * <p>Controller 调用本类构造外部认证响应；Service 禁止依赖 {@code model.dto} 包。
 */
public final class AuthConverter {

    /**
     * 隐藏工具类构造器，禁止创建无状态转换器实例。
     */
    private AuthConverter() {
        // 所有转换方法均为静态方法，不需要实例状态。
    }

    /**
     * 将内部登录结果转换为 OpenAPI 登录响应。
     *
     * @param loginResult 认证 Service 返回的不可变登录结果
     * @return 仅由 Controller 返回给客户端的 OpenAPI 登录响应
     */
    public static LoginResponse toLoginResponse(LoginResult loginResult) {
        // 创建 OpenAPI 定义的用户嵌套对象，避免 Service 直接实例化生成 DTO。
        LoginUser loginUser = new LoginUser();
        loginUser.setId(loginResult.userId());
        loginUser.setUsername(loginResult.username());
        loginUser.setRealName(loginResult.realName());
        loginUser.setRoles(loginResult.roles());

        // 创建 OpenAPI 定义的外部登录响应并填充令牌与用户信息。
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setAccessToken(loginResult.accessToken());
        loginResponse.setTokenType(loginResult.tokenType());
        loginResponse.setExpiresInSeconds(loginResult.expiresInSeconds());
        loginResponse.setUser(loginUser);
        return loginResponse;
    }

    /**
     * 将内部当前用户结果转换为 OpenAPI 当前用户响应。
     *
     * @param currentUserResult 认证 Service 返回的不可变用户档案结果
     * @return 面向客户端的 OpenAPI 当前用户响应
     */
    public static CurrentUserResponse toCurrentUserResponse(CurrentUserResult currentUserResult) {
        // 创建 OpenAPI 用户响应，并逐项映射领域层已经校验过的用户档案值。
        CurrentUserResponse currentUserResponse = new CurrentUserResponse();
        currentUserResponse.setId(currentUserResult.id());
        currentUserResponse.setUsername(currentUserResult.username());
        currentUserResponse.setRealName(currentUserResult.realName());
        currentUserResponse.setPhone(currentUserResult.phone());
        currentUserResponse.setEmail(currentUserResult.email());
        currentUserResponse.setOrganizationName(currentUserResult.organizationName());
        currentUserResponse.setStatus(CurrentUserResponse.StatusEnum.fromValue(currentUserResult.status()));
        currentUserResponse.setRoles(currentUserResult.roles().stream()
                .map(AuthConverter::toCurrentUserRole)
                .toList());
        currentUserResponse.setCreatedAt(currentUserResult.createdAt());
        return currentUserResponse;
    }

    /**
     * 将内部角色结果转换为 OpenAPI 角色 DTO。
     *
     * @param roleResult 内部用户角色结果
     * @return 面向客户端的 OpenAPI 用户角色 DTO
     */
    public static CurrentUserRole toCurrentUserRole(CurrentUserRoleResult roleResult) {
        // 创建 OpenAPI 角色对象并保留角色的标识、编码与展示名称。
        CurrentUserRole currentUserRole = new CurrentUserRole();
        currentUserRole.setId(roleResult.id());
        currentUserRole.setRoleCode(roleResult.roleCode());
        currentUserRole.setRoleName(roleResult.roleName());
        return currentUserRole;
    }
}
