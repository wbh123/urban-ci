package org.urbansafe.priority.spatial;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.common.security.CommunityAccessScope;

/**
 * 正式地图空间查询服务。
 *
 * <p>只调用仓储层的 VERIFIED 查询；bbox 防止一次读取全量几何，zoom 仅控制拓扑保持简化。
 */
@Service
public class SpatialGeoJsonService {

    private static final String BBOX_INVALID = "SPATIAL_BBOX_INVALID";
    private static final String ZOOM_INVALID = "SPATIAL_ZOOM_INVALID";

    private final SpatialBoundaryRepository repository;
    private final BusinessAccessService accessService;

    public SpatialGeoJsonService(
            SpatialBoundaryRepository repository,
            BusinessAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public List<SpatialMapFeature> queryCommunities(
            double west,
            double south,
            double east,
            double north,
            int zoom) {
        validateViewport(west, south, east, north, zoom);
        CommunityAccessScope scope = accessService.currentCommunityScope();
        return repository.queryVerifiedCommunities(
                west, south, east, north, simplifyTolerance(zoom), scope);
    }

    @Transactional(readOnly = true)
    public List<SpatialMapFeature> queryBuildings(
            double west,
            double south,
            double east,
            double north,
            int zoom,
            UUID communityId) {
        validateViewport(west, south, east, north, zoom);
        CommunityAccessScope scope = accessService.currentCommunityScope();
        if (communityId != null) {
            accessService.assertCanReadCommunity(communityId);
        }
        return repository.queryVerifiedBuildings(
                west, south, east, north, simplifyTolerance(zoom), communityId, scope);
    }

    private void validateViewport(
            double west,
            double south,
            double east,
            double north,
            int zoom) {
        if (!Double.isFinite(west)
                || !Double.isFinite(south)
                || !Double.isFinite(east)
                || !Double.isFinite(north)
                || west < -180.0
                || east > 180.0
                || south < -90.0
                || north > 90.0
                || west >= east
                || south >= north) {
            throw new InvalidRequestException(BBOX_INVALID, "地图 bbox 参数无效");
        }
        if (zoom < 3 || zoom > 22) {
            throw new InvalidRequestException(ZOOM_INVALID, "地图 zoom 必须位于 3 到 22 之间");
        }
    }

    private double simplifyTolerance(int zoom) {
        if (zoom >= 17) {
            return 0.0;
        }
        if (zoom >= 15) {
            return 0.000005;
        }
        if (zoom >= 13) {
            return 0.00002;
        }
        return 0.00008;
    }
}
