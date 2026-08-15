package org.urbansafe.priority.ai.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.common.security.BusinessAccessService;

/**
 * Spring AI 视觉工具的资产对象级访问校验。
 *
 * <p>浏览器只提交 assetId/buildingId；服务端根据资产绑定反查真实楼栋，只有资产确实属于
 * 当前楼栋且当前用户拥有该楼栋读取权限时，才允许视觉 Tool 读取图片字节。
 */
@Service
public class AiVisionAssetAccessService {

    private final NamedParameterJdbcTemplate jdbc;
    private final BusinessAccessService accessService;

    public AiVisionAssetAccessService(
            NamedParameterJdbcTemplate jdbc,
            BusinessAccessService accessService) {
        this.jdbc = jdbc;
        this.accessService = accessService;
    }

    public void assertCanReadAssetForBuilding(UUID assetId, UUID expectedBuildingId) {
        if (assetId == null || expectedBuildingId == null) {
            throw new AccessDeniedException("AI_VISION_ASSET_BUILDING_MISMATCH");
        }
        List<UUID> buildingIds = jdbc.queryForList("""
                SELECT DISTINCT resolved.building_id
                FROM (
                    SELECT CASE
                        WHEN b.business_type='BUILDING' THEN b.business_id
                        WHEN b.business_type='INSPECTION_TASK' THEN t.building_id
                        WHEN b.business_type='INSPECTION_RECORD' THEN r.building_id
                        ELSE NULL
                    END AS building_id
                    FROM asset.asset_binding b
                    LEFT JOIN core.inspection_task t
                      ON b.business_type='INSPECTION_TASK'
                     AND t.id=b.business_id
                     AND t.deleted_at IS NULL
                    LEFT JOIN core.inspection_record r
                      ON b.business_type='INSPECTION_RECORD'
                     AND r.id=b.business_id
                     AND r.deleted_at IS NULL
                    WHERE b.asset_id=:assetId
                      AND b.deleted_at IS NULL
                ) resolved
                WHERE resolved.building_id IS NOT NULL
                """, Map.of("assetId", assetId), UUID.class);
        if (!buildingIds.contains(expectedBuildingId)) {
            throw new AccessDeniedException("AI_VISION_ASSET_BUILDING_MISMATCH");
        }
        accessService.assertCanReadBuilding(expectedBuildingId);
    }
}
