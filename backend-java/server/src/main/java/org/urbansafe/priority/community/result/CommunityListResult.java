package org.urbansafe.priority.community.result;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 小区分页列表的轻量业务结果。
 *
 * @param id 小区标识
 * @param communityCode 小区编码
 * @param communityName 小区名称
 * @param administrativeRegion 行政区域
 * @param address 地址
 * @param buildingCount 有效楼栋数
 * @param householdCount 户数
 * @param residentCount 居民数
 * @param status 状态
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record CommunityListResult(
        UUID id,
        String communityCode,
        String communityName,
        String administrativeRegion,
        String address,
        Integer buildingCount,
        Integer householdCount,
        Integer residentCount,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
