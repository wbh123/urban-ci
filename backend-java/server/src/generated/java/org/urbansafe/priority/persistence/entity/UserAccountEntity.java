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

/** core.user_account 表的自动生成持久化实体；禁止手工修改。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@TableName(value = "user_account", schema = "core", autoResultMap = true)
public class UserAccountEntity {

    @TableId(type = IdType.INPUT)
    /** 数据库字段 `id` 映射的 Java 属性。 */
    private UUID id;

    /** 数据库字段 `username` 映射的 Java 属性。 */
    private String username;

    /** 数据库字段 `password_hash` 映射的 Java 属性。 */
    private String passwordHash;

    /** 数据库字段 `real_name` 映射的 Java 属性。 */
    private String realName;

    /** 数据库字段 `phone` 映射的 Java 属性。 */
    private String phone;

    /** 数据库字段 `email` 映射的 Java 属性。 */
    private String email;

    /** 数据库字段 `organization_name` 映射的 Java 属性。 */
    private String organizationName;

    /** 数据库字段 `status` 映射的 Java 属性。 */
    private String status;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `profile` 映射的 Java 属性。 */
    private JsonNode profile;

    @TableField(fill = FieldFill.INSERT)
    /** 数据库字段 `created_at` 映射的 Java 属性。 */
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 数据库字段 `updated_at` 映射的 Java 属性。 */
    private OffsetDateTime updatedAt;

    @TableLogic(value = "null", delval = "now()")
    /** 数据库字段 `deleted_at` 映射的 Java 属性。 */
    private OffsetDateTime deletedAt;

    /** 数据库字段 `remark` 映射的 Java 属性。 */
    private String remark;

    @Version
    /** 数据库字段 `version` 映射的 Java 属性。 */
    private Long version;

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
     * 读取数据库字段 `username` 对应的属性值。
     *
     * @return `username` 的当前值
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置数据库字段 `username` 对应的属性值。
     *
     * @param username 写入 `username` 的值
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 读取数据库字段 `password_hash` 对应的属性值。
     *
     * @return `password_hash` 的当前值
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * 设置数据库字段 `password_hash` 对应的属性值。
     *
     * @param passwordHash 写入 `password_hash` 的值
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 读取数据库字段 `real_name` 对应的属性值。
     *
     * @return `real_name` 的当前值
     */
    public String getRealName() {
        return realName;
    }

    /**
     * 设置数据库字段 `real_name` 对应的属性值。
     *
     * @param realName 写入 `real_name` 的值
     */
    public void setRealName(String realName) {
        this.realName = realName;
    }

    /**
     * 读取数据库字段 `phone` 对应的属性值。
     *
     * @return `phone` 的当前值
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置数据库字段 `phone` 对应的属性值。
     *
     * @param phone 写入 `phone` 的值
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 读取数据库字段 `email` 对应的属性值。
     *
     * @return `email` 的当前值
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置数据库字段 `email` 对应的属性值。
     *
     * @param email 写入 `email` 的值
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 读取数据库字段 `organization_name` 对应的属性值。
     *
     * @return `organization_name` 的当前值
     */
    public String getOrganizationName() {
        return organizationName;
    }

    /**
     * 设置数据库字段 `organization_name` 对应的属性值。
     *
     * @param organizationName 写入 `organization_name` 的值
     */
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    /**
     * 读取数据库字段 `status` 对应的属性值。
     *
     * @return `status` 的当前值
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置数据库字段 `status` 对应的属性值。
     *
     * @param status 写入 `status` 的值
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 读取数据库字段 `profile` 对应的属性值。
     *
     * @return `profile` 的当前值
     */
    public JsonNode getProfile() {
        return profile;
    }

    /**
     * 设置数据库字段 `profile` 对应的属性值。
     *
     * @param profile 写入 `profile` 的值
     */
    public void setProfile(JsonNode profile) {
        this.profile = profile;
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

    /**
     * 读取数据库字段 `remark` 对应的属性值。
     *
     * @return `remark` 的当前值
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置数据库字段 `remark` 对应的属性值。
     *
     * @param remark 写入 `remark` 的值
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 读取数据库字段 `version` 对应的属性值。
     *
     * @return `version` 的当前值
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 设置数据库字段 `version` 对应的属性值。
     *
     * @param version 写入 `version` 的值
     */
    public void setVersion(Long version) {
        this.version = version;
    }

}
