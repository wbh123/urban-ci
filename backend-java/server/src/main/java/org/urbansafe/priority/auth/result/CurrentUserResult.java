package org.urbansafe.priority.auth.result;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 表示当前登录用户档案的内部不可变结果。
 *
 * @param id 用户唯一标识
 * @param username 用户名
 * @param realName 真实姓名
 * @param phone 脱敏后的联系电话
 * @param email 脱敏后的电子邮箱
 * @param organizationName 所属组织名称
 * @param status 账号状态业务值
 * @param roles 角色详情列表
 * @param createdAt 用户创建时间
 */
public record CurrentUserResult(
        UUID id,
        String username,
        String realName,
        String phone,
        String email,
        String organizationName,
        String status,
        List<CurrentUserRoleResult> roles,
        OffsetDateTime createdAt) {

    /**
     * 复制角色集合，确保结果离开 Service 后仍保持不可变。
     */
    public CurrentUserResult {
        // 角色集合是用户档案快照，必须防止 Controller 或调用方改变其元素顺序。
        roles = List.copyOf(roles);
    }
}
