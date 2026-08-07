package org.urbansafe.priority.community.result;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service 返回的小区详情结果，不携带数据库注解或 OpenAPI 生成类型。
 *
 * @param id 小区标识
 * @param communityCode 小区编码
 * @param communityName 小区名称
 * @param administrativeRegion 行政区域
 * @param address 地址
 * @param constructionPeriod 建成年代
 * @param buildingCount 有效楼栋数量
 * @param householdCount 户数
 * @param residentCount 居民数
 * @param archiveCompletenessScore 档案完整度
 * @param status 状态
 * @param extraAttributes 扩展属性
 * @param remark 备注
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param version 乐观锁版本号
 */
public record CommunityDetailResult(
        UUID id,
        String communityCode,
        String communityName,
        String administrativeRegion,
        String address,
        String constructionPeriod,
        Integer buildingCount,
        Integer householdCount,
        Integer residentCount,
        BigDecimal archiveCompletenessScore,
        String status,
        JsonNode extraAttributes,
        String remark,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version) {
}
