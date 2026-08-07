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

/** core.community 表的自动生成持久化实体；禁止手工修改。 */
@Generated("org.urbansafe.priority.codegen.PersistenceCodeGenerator")
@TableName(value = "community", schema = "core", autoResultMap = true)
public class CommunityEntity {

    @TableId(type = IdType.INPUT)
    /** 数据库字段 `id` 映射的 Java 属性。 */
    private UUID id;

    /** 数据库字段 `community_code` 映射的 Java 属性。 */
    private String communityCode;

    /** 数据库字段 `community_name` 映射的 Java 属性。 */
    private String communityName;

    /** 数据库字段 `administrative_region` 映射的 Java 属性。 */
    private String administrativeRegion;

    /** 数据库字段 `address` 映射的 Java 属性。 */
    private String address;

    /** 数据库字段 `construction_period` 映射的 Java 属性。 */
    private String constructionPeriod;

    /** 数据库字段 `building_count` 映射的 Java 属性。 */
    private Integer buildingCount;

    /** 数据库字段 `household_count` 映射的 Java 属性。 */
    private Integer householdCount;

    /** 数据库字段 `resident_count` 映射的 Java 属性。 */
    private Integer residentCount;

    /** 数据库字段 `archive_completeness_score` 映射的 Java 属性。 */
    private BigDecimal archiveCompletenessScore;

    /** 数据库字段 `status` 映射的 Java 属性。 */
    private String status;

    @TableField(typeHandler = JsonNodeTypeHandler.class)
    /** 数据库字段 `extra_attributes` 映射的 Java 属性。 */
    private JsonNode extraAttributes;

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
     * 读取数据库字段 `community_code` 对应的属性值。
     *
     * @return `community_code` 的当前值
     */
    public String getCommunityCode() {
        return communityCode;
    }

    /**
     * 设置数据库字段 `community_code` 对应的属性值。
     *
     * @param communityCode 写入 `community_code` 的值
     */
    public void setCommunityCode(String communityCode) {
        this.communityCode = communityCode;
    }

    /**
     * 读取数据库字段 `community_name` 对应的属性值。
     *
     * @return `community_name` 的当前值
     */
    public String getCommunityName() {
        return communityName;
    }

    /**
     * 设置数据库字段 `community_name` 对应的属性值。
     *
     * @param communityName 写入 `community_name` 的值
     */
    public void setCommunityName(String communityName) {
        this.communityName = communityName;
    }

    /**
     * 读取数据库字段 `administrative_region` 对应的属性值。
     *
     * @return `administrative_region` 的当前值
     */
    public String getAdministrativeRegion() {
        return administrativeRegion;
    }

    /**
     * 设置数据库字段 `administrative_region` 对应的属性值。
     *
     * @param administrativeRegion 写入 `administrative_region` 的值
     */
    public void setAdministrativeRegion(String administrativeRegion) {
        this.administrativeRegion = administrativeRegion;
    }

    /**
     * 读取数据库字段 `address` 对应的属性值。
     *
     * @return `address` 的当前值
     */
    public String getAddress() {
        return address;
    }

    /**
     * 设置数据库字段 `address` 对应的属性值。
     *
     * @param address 写入 `address` 的值
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * 读取数据库字段 `construction_period` 对应的属性值。
     *
     * @return `construction_period` 的当前值
     */
    public String getConstructionPeriod() {
        return constructionPeriod;
    }

    /**
     * 设置数据库字段 `construction_period` 对应的属性值。
     *
     * @param constructionPeriod 写入 `construction_period` 的值
     */
    public void setConstructionPeriod(String constructionPeriod) {
        this.constructionPeriod = constructionPeriod;
    }

    /**
     * 读取数据库字段 `building_count` 对应的属性值。
     *
     * @return `building_count` 的当前值
     */
    public Integer getBuildingCount() {
        return buildingCount;
    }

    /**
     * 设置数据库字段 `building_count` 对应的属性值。
     *
     * @param buildingCount 写入 `building_count` 的值
     */
    public void setBuildingCount(Integer buildingCount) {
        this.buildingCount = buildingCount;
    }

    /**
     * 读取数据库字段 `household_count` 对应的属性值。
     *
     * @return `household_count` 的当前值
     */
    public Integer getHouseholdCount() {
        return householdCount;
    }

    /**
     * 设置数据库字段 `household_count` 对应的属性值。
     *
     * @param householdCount 写入 `household_count` 的值
     */
    public void setHouseholdCount(Integer householdCount) {
        this.householdCount = householdCount;
    }

    /**
     * 读取数据库字段 `resident_count` 对应的属性值。
     *
     * @return `resident_count` 的当前值
     */
    public Integer getResidentCount() {
        return residentCount;
    }

    /**
     * 设置数据库字段 `resident_count` 对应的属性值。
     *
     * @param residentCount 写入 `resident_count` 的值
     */
    public void setResidentCount(Integer residentCount) {
        this.residentCount = residentCount;
    }

    /**
     * 读取数据库字段 `archive_completeness_score` 对应的属性值。
     *
     * @return `archive_completeness_score` 的当前值
     */
    public BigDecimal getArchiveCompletenessScore() {
        return archiveCompletenessScore;
    }

    /**
     * 设置数据库字段 `archive_completeness_score` 对应的属性值。
     *
     * @param archiveCompletenessScore 写入 `archive_completeness_score` 的值
     */
    public void setArchiveCompletenessScore(BigDecimal archiveCompletenessScore) {
        this.archiveCompletenessScore = archiveCompletenessScore;
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
     * 读取数据库字段 `extra_attributes` 对应的属性值。
     *
     * @return `extra_attributes` 的当前值
     */
    public JsonNode getExtraAttributes() {
        return extraAttributes;
    }

    /**
     * 设置数据库字段 `extra_attributes` 对应的属性值。
     *
     * @param extraAttributes 写入 `extra_attributes` 的值
     */
    public void setExtraAttributes(JsonNode extraAttributes) {
        this.extraAttributes = extraAttributes;
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
