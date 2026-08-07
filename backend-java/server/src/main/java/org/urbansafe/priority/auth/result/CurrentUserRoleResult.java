package org.urbansafe.priority.auth.result;

import java.util.UUID;

/**
 * 表示当前用户所属角色的内部不可变结果。
 *
 * @param id 角色唯一标识
 * @param roleCode 角色业务编码
 * @param roleName 角色显示名称
 */
public record CurrentUserRoleResult(UUID id, String roleCode, String roleName) {
}
