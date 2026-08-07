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

/** audit.operation_log 表的自动生成持久化实体；禁止手工修改。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@TableName(value = "operation_log", schema = "audit", autoResultMap = true)
public class OperationLogEntity {

    @TableId(type = IdType.INPUT)
    /** 数据库字段 `id` 映射的 Java 属性。 */
    private UUID id;

    /** 数据库字段 `user_id` 映射的 Java 属性。 */
    private UUID userId;

    /** 数据库字段 `operation_type` 映射的 Java 属性。 */
    private String operationType;

    /** 数据库字段 `resource_type` 映射的 Java 属性。 */
    private String resourceType;

    /** 数据库字段 `resource_id` 映射的 Java 属性。 */
    private UUID resourceId;

    /** 数据库字段 `request_id` 映射的 Java 属性。 */
    private String requestId;

    /** 数据库字段 `client_ip` 映射的 Java 属性。 */
    private String clientIp;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `operation_detail` 映射的 Java 属性。 */
    private JsonNode operationDetail;

    /** 数据库字段 `success` 映射的 Java 属性。 */
    private Boolean success;

    /** 数据库字段 `error_code` 映射的 Java 属性。 */
    private String errorCode;

    /** 数据库字段 `error_message` 映射的 Java 属性。 */
    private String errorMessage;

    /** 数据库字段 `operated_at` 映射的 Java 属性。 */
    private OffsetDateTime operatedAt;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `before_data` 映射的 Java 属性。 */
    private JsonNode beforeData;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `after_data` 映射的 Java 属性。 */
    private JsonNode afterData;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `changed_fields` 映射的 Java 属性。 */
    private JsonNode changedFields;

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
     * 读取数据库字段 `operation_type` 对应的属性值。
     *
     * @return `operation_type` 的当前值
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * 设置数据库字段 `operation_type` 对应的属性值。
     *
     * @param operationType 写入 `operation_type` 的值
     */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * 读取数据库字段 `resource_type` 对应的属性值。
     *
     * @return `resource_type` 的当前值
     */
    public String getResourceType() {
        return resourceType;
    }

    /**
     * 设置数据库字段 `resource_type` 对应的属性值。
     *
     * @param resourceType 写入 `resource_type` 的值
     */
    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    /**
     * 读取数据库字段 `resource_id` 对应的属性值。
     *
     * @return `resource_id` 的当前值
     */
    public UUID getResourceId() {
        return resourceId;
    }

    /**
     * 设置数据库字段 `resource_id` 对应的属性值。
     *
     * @param resourceId 写入 `resource_id` 的值
     */
    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * 读取数据库字段 `request_id` 对应的属性值。
     *
     * @return `request_id` 的当前值
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * 设置数据库字段 `request_id` 对应的属性值。
     *
     * @param requestId 写入 `request_id` 的值
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * 读取数据库字段 `client_ip` 对应的属性值。
     *
     * @return `client_ip` 的当前值
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * 设置数据库字段 `client_ip` 对应的属性值。
     *
     * @param clientIp 写入 `client_ip` 的值
     */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    /**
     * 读取数据库字段 `operation_detail` 对应的属性值。
     *
     * @return `operation_detail` 的当前值
     */
    public JsonNode getOperationDetail() {
        return operationDetail;
    }

    /**
     * 设置数据库字段 `operation_detail` 对应的属性值。
     *
     * @param operationDetail 写入 `operation_detail` 的值
     */
    public void setOperationDetail(JsonNode operationDetail) {
        this.operationDetail = operationDetail;
    }

    /**
     * 读取数据库字段 `success` 对应的属性值。
     *
     * @return `success` 的当前值
     */
    public Boolean getSuccess() {
        return success;
    }

    /**
     * 设置数据库字段 `success` 对应的属性值。
     *
     * @param success 写入 `success` 的值
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * 读取数据库字段 `error_code` 对应的属性值。
     *
     * @return `error_code` 的当前值
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 设置数据库字段 `error_code` 对应的属性值。
     *
     * @param errorCode 写入 `error_code` 的值
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 读取数据库字段 `error_message` 对应的属性值。
     *
     * @return `error_message` 的当前值
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 设置数据库字段 `error_message` 对应的属性值。
     *
     * @param errorMessage 写入 `error_message` 的值
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 读取数据库字段 `operated_at` 对应的属性值。
     *
     * @return `operated_at` 的当前值
     */
    public OffsetDateTime getOperatedAt() {
        return operatedAt;
    }

    /**
     * 设置数据库字段 `operated_at` 对应的属性值。
     *
     * @param operatedAt 写入 `operated_at` 的值
     */
    public void setOperatedAt(OffsetDateTime operatedAt) {
        this.operatedAt = operatedAt;
    }

    /**
     * 读取数据库字段 `before_data` 对应的属性值。
     *
     * @return `before_data` 的当前值
     */
    public JsonNode getBeforeData() {
        return beforeData;
    }

    /**
     * 设置数据库字段 `before_data` 对应的属性值。
     *
     * @param beforeData 写入 `before_data` 的值
     */
    public void setBeforeData(JsonNode beforeData) {
        this.beforeData = beforeData;
    }

    /**
     * 读取数据库字段 `after_data` 对应的属性值。
     *
     * @return `after_data` 的当前值
     */
    public JsonNode getAfterData() {
        return afterData;
    }

    /**
     * 设置数据库字段 `after_data` 对应的属性值。
     *
     * @param afterData 写入 `after_data` 的值
     */
    public void setAfterData(JsonNode afterData) {
        this.afterData = afterData;
    }

    /**
     * 读取数据库字段 `changed_fields` 对应的属性值。
     *
     * @return `changed_fields` 的当前值
     */
    public JsonNode getChangedFields() {
        return changedFields;
    }

    /**
     * 设置数据库字段 `changed_fields` 对应的属性值。
     *
     * @param changedFields 写入 `changed_fields` 的值
     */
    public void setChangedFields(JsonNode changedFields) {
        this.changedFields = changedFields;
    }

}
