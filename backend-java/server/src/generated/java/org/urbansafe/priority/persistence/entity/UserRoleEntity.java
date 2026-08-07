package org.urbansafe.priority.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Generated;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.urbansafe.priority.persistence.typehandler.JsonNodeTypeHandler;

/** core.user_role 表的自动生成持久化实体；禁止手工修改。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@TableName(value = "user_role", schema = "core")
public class UserRoleEntity {

    @TableId(type = IdType.INPUT)
    /** 数据库字段 `id` 映射的 Java 属性。 */
    private UUID id;

    /** 数据库字段 `user_id` 映射的 Java 属性。 */
    private UUID userId;

    /** 数据库字段 `role_id` 映射的 Java 属性。 */
    private UUID roleId;

    @TableField(fill = FieldFill.INSERT)
    /** 数据库字段 `created_at` 映射的 Java 属性。 */
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 数据库字段 `updated_at` 映射的 Java 属性。 */
    private OffsetDateTime updatedAt;

    @TableLogic(value = "null", delval = "now()")
    /** 数据库字段 `deleted_at` 映射的 Java 属性。 */
    private OffsetDateTime deletedAt;

    /**
     * 读取数据库字段 `id` 对应的属性值。
     *
     * @return `id` 的当前值
     */
    public UUID getId() {
        return id;
    }

    /**
     * 设置数据库字段 `id` 对应的属性值。
     *
     * @param id 写入 `id` 的值
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * 读取数据库字段 `user_id` 对应的属性值。
     *
     * @return `user_id` 的当前值
     */
    public UUID getUserId() {
        return userId;
    }

    /**
     * 设置数据库字段 `user_id` 对应的属性值。
     *
     * @param userId 写入 `user_id` 的值
     */
    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    /**
     * 读取数据库字段 `role_id` 对应的属性值。
     *
     * @return `role_id` 的当前值
     */
    public UUID getRoleId() {
        return roleId;
    }

    /**
     * 设置数据库字段 `role_id` 对应的属性值。
     *
     * @param roleId 写入 `role_id` 的值
     */
    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    /**
     * 读取数据库字段 `created_at` 对应的属性值。
     *
     * @return `created_at` 的当前值
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置数据库字段 `created_at` 对应的属性值。
     *
     * @param createdAt 写入 `created_at` 的值
     */
    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 读取数据库字段 `updated_at` 对应的属性值。
     *
     * @return `updated_at` 的当前值
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置数据库字段 `updated_at` 对应的属性值。
     *
     * @param updatedAt 写入 `updated_at` 的值
     */
    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 读取数据库字段 `deleted_at` 对应的属性值。
     *
     * @return `deleted_at` 的当前值
     */
    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    /**
     * 设置数据库字段 `deleted_at` 对应的属性值。
     *
     * @param deletedAt 写入 `deleted_at` 的值
     */
    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

}
