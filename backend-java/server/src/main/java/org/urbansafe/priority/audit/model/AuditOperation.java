package org.urbansafe.priority.audit.model;

import java.util.List;
import java.util.UUID;

/**
 * 业务层提交给统一审计服务的操作描述。
 *
 * <p>该模型不依赖数据库实体，也不依赖 OpenAPI DTO；业务服务只描述“发生了什么”，具体持久化、时间、
 * 请求编号和脱敏均由审计服务统一完成。
 *
 * @param operationType 稳定操作类型，例如 CREATE、UPDATE、DELETE 或 AUTH_LOGIN_SUCCESS
 * @param resourceType 资源类型，例如 Community、Building
 * @param resourceId 资源标识；登录失败等无资源场景允许为空
 * @param beforeData 操作前快照；无快照时允许为空
 * @param afterData 操作后快照；无快照时允许为空
 * @param changedFields 发生变化的字段名；无变化时传空列表
 * @param summary 不含敏感信息的操作摘要
 */
public record AuditOperation(
        String operationType,
        String resourceType,
        UUID resourceId,
        Object beforeData,
        Object afterData,
        List<String> changedFields,
        String summary) {

    /**
     * 创建成功操作描述，并把空字段列表规范化为空列表。
     *
     * @param operationType 操作类型
     * @param resourceType 资源类型
     * @param resourceId 资源标识
     * @param beforeData 操作前快照
     * @param afterData 操作后快照
     * @param changedFields 变化字段
     * @param summary 操作摘要
     * @return 不含持久化细节的审计操作
     */
    public static AuditOperation success(
            String operationType,
            String resourceType,
            UUID resourceId,
            Object beforeData,
            Object afterData,
            List<String> changedFields,
            String summary) {
        return new AuditOperation(
                operationType,
                resourceType,
                resourceId,
                beforeData,
                afterData,
                changedFields == null ? List.of() : List.copyOf(changedFields),
                summary);
    }
}
