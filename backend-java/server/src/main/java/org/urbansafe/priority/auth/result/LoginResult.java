package org.urbansafe.priority.auth.result;

import java.util.List;
import java.util.UUID;

/**
 * 表示认证成功后由业务层返回的不可变登录结果。
 *
 * <p>该结果不依赖 OpenAPI 生成 DTO，Controller 必须通过认证转换器将其映射为外部响应。
 *
 * @param userId 已认证用户的唯一标识
 * @param username 已认证用户名
 * @param realName 用户真实姓名
 * @param roles 用户拥有的角色编码列表
 * @param accessToken 已签发的访问令牌
 * @param tokenType 令牌类型
 * @param expiresInSeconds 令牌有效期秒数
 */
public record LoginResult(
        UUID userId,
        String username,
        String realName,
        List<String> roles,
        String accessToken,
        String tokenType,
        int expiresInSeconds) {

    /**
     * 复制角色集合，避免调用方在结果创建后修改登录结果中的角色数据。
     */
    public LoginResult {
        // 角色是认证结果的一部分，复制为不可变集合以维持 record 的值对象语义。
        roles = List.copyOf(roles);
    }
}
