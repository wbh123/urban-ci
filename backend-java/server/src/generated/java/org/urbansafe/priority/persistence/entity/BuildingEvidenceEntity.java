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

/** core.building_evidence 表的自动生成持久化实体；禁止手工修改。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@TableName(value = "building_evidence", schema = "core", autoResultMap = true)
public class BuildingEvidenceEntity {

    @TableId(type = IdType.INPUT)
    /** 数据库字段 `id` 映射的 Java 属性。 */
    private UUID id;

    /** 数据库字段 `building_id` 映射的 Java 属性。 */
    private UUID buildingId;

    /** 数据库字段 `evidence_type` 映射的 Java 属性。 */
    private String evidenceType;

    /** 数据库字段 `title` 映射的 Java 属性。 */
    private String title;

    /** 数据库字段 `description` 映射的 Java 属性。 */
    private String description;

    /** 数据库字段 `occurred_at` 映射的 Java 属性。 */
    private OffsetDateTime occurredAt;

    /** 数据库字段 `source` 映射的 Java 属性。 */
    private String source;

    /** 数据库字段 `reliability_level` 映射的 Java 属性。 */
    private String reliabilityLevel;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `evidence_data` 映射的 Java 属性。 */
    private JsonNode evidenceData;

    /** 数据库字段 `created_by` 映射的 Java 属性。 */
    private UUID createdBy;

    @TableField(fill = FieldFill.INSERT)
    /** 数据库字段 `created_at` 映射的 Java 属性。 */
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    /** 数据库字段 `updated_at` 映射的 Java 属性。 */
    private OffsetDateTime updatedAt;

    @TableLogic(value = "null", delval = "now()")
    /** 数据库字段 `deleted_at` 映射的 Java 属性。 */
    private OffsetDateTime deletedAt;

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
     * 读取数据库字段 `building_id` 对应的属性值。
     *
     * @return `building_id` 的当前值
     */
    public UUID getBuildingId() {
        return buildingId;
    }

    /**
     * 设置数据库字段 `building_id` 对应的属性值。
     *
     * @param buildingId 写入 `building_id` 的值
     */
    public void setBuildingId(UUID buildingId) {
        this.buildingId = buildingId;
    }

    /**
     * 读取数据库字段 `evidence_type` 对应的属性值。
     *
     * @return `evidence_type` 的当前值
     */
    public String getEvidenceType() {
        return evidenceType;
    }

    /**
     * 设置数据库字段 `evidence_type` 对应的属性值。
     *
     * @param evidenceType 写入 `evidence_type` 的值
     */
    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    /**
     * 读取数据库字段 `title` 对应的属性值。
     *
     * @return `title` 的当前值
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置数据库字段 `title` 对应的属性值。
     *
     * @param title 写入 `title` 的值
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 读取数据库字段 `description` 对应的属性值。
     *
     * @return `description` 的当前值
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置数据库字段 `description` 对应的属性值。
     *
     * @param description 写入 `description` 的值
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 读取数据库字段 `occurred_at` 对应的属性值。
     *
     * @return `occurred_at` 的当前值
     */
    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    /**
     * 设置数据库字段 `occurred_at` 对应的属性值。
     *
     * @param occurredAt 写入 `occurred_at` 的值
     */
    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    /**
     * 读取数据库字段 `source` 对应的属性值。
     *
     * @return `source` 的当前值
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置数据库字段 `source` 对应的属性值。
     *
     * @param source 写入 `source` 的值
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 读取数据库字段 `reliability_level` 对应的属性值。
     *
     * @return `reliability_level` 的当前值
     */
    public String getReliabilityLevel() {
        return reliabilityLevel;
    }

    /**
     * 设置数据库字段 `reliability_level` 对应的属性值。
     *
     * @param reliabilityLevel 写入 `reliability_level` 的值
     */
    public void setReliabilityLevel(String reliabilityLevel) {
        this.reliabilityLevel = reliabilityLevel;
    }

    /**
     * 读取数据库字段 `evidence_data` 对应的属性值。
     *
     * @return `evidence_data` 的当前值
     */
    public JsonNode getEvidenceData() {
        return evidenceData;
    }

    /**
     * 设置数据库字段 `evidence_data` 对应的属性值。
     *
     * @param evidenceData 写入 `evidence_data` 的值
     */
    public void setEvidenceData(JsonNode evidenceData) {
        this.evidenceData = evidenceData;
    }

    /**
     * 读取数据库字段 `created_by` 对应的属性值。
     *
     * @return `created_by` 的当前值
     */
    public UUID getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置数据库字段 `created_by` 对应的属性值。
     *
     * @param createdBy 写入 `created_by` 的值
     */
    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
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
