package org.urbansafe.priority.spatial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.security.BusinessAccessService;

/** 空间边界统一领域服务：对象权限、乐观锁、审核状态与 revision 在这里一次性收口。 */
@Service
public class SpatialBoundaryService {

    private static final String VERSION_CONFLICT = "SPATIAL_BOUNDARY_VERSION_CONFLICT";
    private static final String STATE_CONFLICT = "SPATIAL_BOUNDARY_STATE_CONFLICT";
    private static final String NOT_FOUND = "SPATIAL_BOUNDARY_NOT_FOUND";
    private static final String INVALID = "SPATIAL_BOUNDARY_INVALID";
    private static final Set<String> SOURCE_TYPES = Set.of(
            "AMAP_AOI", "MANUAL_EDIT", "MANUAL_DRAW", "GEOJSON_IMPORT");
    private static final Set<String> COORDINATE_SYSTEMS = Set.of("GCJ02", "WGS84", "BD09");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SpatialBoundaryRepository repository;
    private final BusinessAccessService accessService;

    public SpatialBoundaryService(
            SpatialBoundaryRepository repository,
            BusinessAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
    }

    @Transactional
    public SpatialBoundarySnapshot upsertCommunity(UUID communityId, SpatialBoundaryWriteCommand command) {
        accessService.assertCanManageCommunity(communityId);
        return upsert(BoundaryEntityType.COMMUNITY, communityId, command);
    }

    @Transactional
    public SpatialBoundarySnapshot upsertBuilding(UUID buildingId, SpatialBoundaryWriteCommand command) {
        accessService.assertCanManageBuilding(buildingId);
        return upsert(BoundaryEntityType.BUILDING, buildingId, command);
    }

    @Transactional(readOnly = true)
    public SpatialBoundarySnapshot getCommunity(UUID communityId) {
        accessService.assertCanReadCommunity(communityId);
        return requireCurrent(BoundaryEntityType.COMMUNITY, communityId);
    }

    @Transactional(readOnly = true)
    public SpatialBoundarySnapshot getBuilding(UUID buildingId) {
        accessService.assertCanReadBuilding(buildingId);
        return requireCurrent(BoundaryEntityType.BUILDING, buildingId);
    }

    @Transactional
    public SpatialBoundarySnapshot verifyCommunity(UUID communityId, long expectedVersion, String remark) {
        accessService.assertCanManageCommunity(communityId);
        return transition(BoundaryEntityType.COMMUNITY, communityId, expectedVersion,
                BoundaryStatus.VERIFIED, BoundaryChangeType.VERIFY, remark);
    }

    @Transactional
    public SpatialBoundarySnapshot verifyBuilding(UUID buildingId, long expectedVersion, String remark) {
        accessService.assertCanManageBuilding(buildingId);
        return transition(BoundaryEntityType.BUILDING, buildingId, expectedVersion,
                BoundaryStatus.VERIFIED, BoundaryChangeType.VERIFY, remark);
    }

    @Transactional
    public SpatialBoundarySnapshot rejectCommunity(UUID communityId, long expectedVersion, String remark) {
        accessService.assertCanManageCommunity(communityId);
        return transition(BoundaryEntityType.COMMUNITY, communityId, expectedVersion,
                BoundaryStatus.REJECTED, BoundaryChangeType.REJECT, remark);
    }

    @Transactional
    public SpatialBoundarySnapshot rejectBuilding(UUID buildingId, long expectedVersion, String remark) {
        accessService.assertCanManageBuilding(buildingId);
        return transition(BoundaryEntityType.BUILDING, buildingId, expectedVersion,
                BoundaryStatus.REJECTED, BoundaryChangeType.REJECT, remark);
    }

    private SpatialBoundarySnapshot upsert(
            BoundaryEntityType type,
            UUID entityId,
            SpatialBoundaryWriteCommand command) {
        SpatialBoundaryWriteCommand normalized = normalizeCommand(command);
        Optional<SpatialBoundarySnapshot> current = repository.findCurrent(type, entityId);
        UUID actorId = CurrentUser.getUserId();
        try {
            SpatialBoundarySnapshot saved;
            if (current.isEmpty()) {
                if (normalized.expectedVersion() != 0L) {
                    throw versionConflict();
                }
                saved = repository.insertUnverified(type, entityId, normalized, 1L);
            } else {
                SpatialBoundarySnapshot existing = current.get();
                if (normalized.expectedVersion() != existing.version()) {
                    throw versionConflict();
                }
                long nextVersion = existing.version() + 1L;
                saved = repository.updateUnverified(
                                type, entityId, normalized, existing.version(), nextVersion)
                        .orElseThrow(this::versionConflict);
            }
            repository.appendRevision(saved, BoundaryChangeType.UPSERT, actorId);
            return saved;
        } catch (ResourceConflictException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new InvalidRequestException(INVALID, "边界几何无法写入，请检查 GeoJSON 和坐标数据");
        }
    }

    private SpatialBoundarySnapshot transition(
            BoundaryEntityType type,
            UUID entityId,
            long expectedVersion,
            BoundaryStatus targetStatus,
            BoundaryChangeType changeType,
            String remark) {
        SpatialBoundarySnapshot current = requireCurrent(type, entityId);
        if (current.version() != expectedVersion) {
            throw versionConflict();
        }
        if (current.status() != BoundaryStatus.UNVERIFIED) {
            throw new ResourceConflictException(
                    STATE_CONFLICT, "只有未确认边界可以执行确认或驳回操作");
        }

        UUID actorId = CurrentUser.getUserId();
        SpatialBoundarySnapshot changed = repository.transitionStatus(
                        type,
                        entityId,
                        expectedVersion,
                        expectedVersion + 1L,
                        targetStatus,
                        actorId,
                        normalizeRemark(remark))
                .orElseThrow(this::versionConflict);
        repository.appendRevision(changed, changeType, actorId);
        return changed;
    }

    private SpatialBoundarySnapshot requireCurrent(BoundaryEntityType type, UUID entityId) {
        return repository.findCurrent(type, entityId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND, "空间边界不存在"));
    }

    private SpatialBoundaryWriteCommand normalizeCommand(SpatialBoundaryWriteCommand command) {
        if (command == null || command.expectedVersion() < 0L) {
            throw new InvalidRequestException(INVALID, "expectedVersion 不能为空且不能小于 0");
        }
        String sourceType = upper(command.sourceType());
        String sourceCoordinateSystem = upper(command.sourceCoordinateSystem());
        String displayCoordinateSystem = upper(command.displayCoordinateSystem());
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new InvalidRequestException(INVALID, "不支持的边界来源类型");
        }
        if (!COORDINATE_SYSTEMS.contains(sourceCoordinateSystem)
                || !COORDINATE_SYSTEMS.contains(displayCoordinateSystem)) {
            throw new InvalidRequestException(INVALID, "不支持的边界坐标系");
        }
        validateJson(command.sourceGeometryJson(), false);
        validateJson(command.displayGeometryJson(), true);
        return new SpatialBoundaryWriteCommand(
                command.expectedVersion(),
                sourceType,
                normalizeNullable(command.sourceProvider()),
                normalizeNullable(command.sourceObjectId()),
                sourceCoordinateSystem,
                command.sourceGeometryJson().trim(),
                displayCoordinateSystem,
                command.displayGeometryJson().trim(),
                normalizeRemark(command.remark()));
    }

    private void validateJson(String json, boolean requirePolygon) {
        if (json == null || json.isBlank()) {
            throw new InvalidRequestException(INVALID, "边界 GeoJSON 不能为空");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.isObject()) {
                throw new InvalidRequestException(INVALID, "边界 GeoJSON 必须是对象");
            }
            if (requirePolygon) {
                String geometryType = root.path("type").asText("");
                if (!("Polygon".equals(geometryType) || "MultiPolygon".equals(geometryType))
                        || !root.path("coordinates").isArray()) {
                    throw new InvalidRequestException(INVALID, "展示边界必须是 Polygon 或 MultiPolygon GeoJSON");
                }
            }
        } catch (InvalidRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidRequestException(INVALID, "边界 GeoJSON 格式无效");
        }
    }

    private ResourceConflictException versionConflict() {
        return new ResourceConflictException(VERSION_CONFLICT, "边界已被其他请求修改，请刷新后重试");
    }

    private String normalizeRemark(String remark) {
        return normalizeNullable(remark);
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
