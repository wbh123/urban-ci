package org.urbansafe.priority.spatial.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.common.security.CommunityAccessScope;

/** 空间边界的持久化、版本控制、权限过滤与 GeoJSON 视野查询。 */
@Service
public class SpatialBoundaryService {

    private static final Set<String> SOURCE_TYPES = Set.of(
            "AMAP_AOI", "MANUAL_EDIT", "MANUAL_DRAW", "GEOJSON_IMPORT");
    private static final Set<String> SOURCE_PROVIDERS = Set.of(
            "AMAP", "INTERNAL", "EXTERNAL_GIS");
    private static final Set<String> COORDINATE_SYSTEMS = Set.of("GCJ02", "WGS84");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final BusinessAccessService accessService;

    public SpatialBoundaryService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            BusinessAccessService accessService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.accessService = accessService;
    }

    public Map<String, Object> getCommunityBoundary(UUID communityId) {
        accessService.assertCanReadCommunity(communityId);
        return requireBoundary(EntityType.COMMUNITY, communityId);
    }

    public Map<String, Object> getBuildingBoundary(UUID buildingId) {
        accessService.assertCanReadBuilding(buildingId);
        return requireBoundary(EntityType.BUILDING, buildingId);
    }

    @Transactional
    public Map<String, Object> upsertCommunityBoundary(
            UUID communityId, Map<String, Object> request) {
        accessService.assertCanManageCommunity(communityId);
        return upsert(EntityType.COMMUNITY, communityId, request);
    }

    @Transactional
    public Map<String, Object> upsertBuildingBoundary(
            UUID buildingId, Map<String, Object> request) {
        accessService.assertCanManageBuilding(buildingId);
        return upsert(EntityType.BUILDING, buildingId, request);
    }

    @Transactional
    public Map<String, Object> verifyCommunityBoundary(
            UUID communityId, int expectedVersion, String remark) {
        accessService.assertCanManageCommunity(communityId);
        return verify(EntityType.COMMUNITY, communityId, expectedVersion, remark);
    }

    @Transactional
    public Map<String, Object> verifyBuildingBoundary(
            UUID buildingId, int expectedVersion, String remark) {
        accessService.assertCanManageBuilding(buildingId);
        return verify(EntityType.BUILDING, buildingId, expectedVersion, remark);
    }

    public List<Map<String, Object>> listCommunityBoundaryRevisions(UUID communityId) {
        accessService.assertCanReadCommunity(communityId);
        return revisions(EntityType.COMMUNITY, communityId);
    }

    public List<Map<String, Object>> listBuildingBoundaryRevisions(UUID buildingId) {
        accessService.assertCanReadBuilding(buildingId);
        return revisions(EntityType.BUILDING, buildingId);
    }

    public Map<String, Object> listCommunityFeatures(
            double west, double south, double east, double north, int zoom) {
        validateViewport(west, south, east, north, zoom);
        CommunityAccessScope scope = accessService.currentCommunityScope();
        if (!scope.global() && scope.communityIds().isEmpty()) {
            return featureCollection(List.of());
        }
        double tolerance = simplificationTolerance(zoom);
        MapSqlParameterSource params = viewportParams(west, south, east, north, tolerance);
        String scopeSql = "";
        if (!scope.global()) {
            scopeSql = " AND cb.community_id IN (:communityIds)";
            params.addValue("communityIds", scope.communityIds());
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cb.community_id AS entity_id,
                       c.community_name AS entity_name,
                       cb.status AS boundary_status,
                       ST_AsGeoJSON(
                           CASE WHEN :tolerance > 0
                                THEN ST_SimplifyPreserveTopology(cb.display_geometry, :tolerance)
                                ELSE cb.display_geometry END
                       ) AS geometry_json
                FROM geo.community_boundary cb
                JOIN core.community c ON c.id=cb.community_id AND c.deleted_at IS NULL
                WHERE cb.deleted_at IS NULL
                  AND cb.status='VERIFIED'
                  AND cb.display_geometry IS NOT NULL
                  AND cb.display_geometry && ST_MakeEnvelope(:west, :south, :east, :north)
                """ + scopeSql + " ORDER BY c.community_name", params);

        List<Map<String, Object>> features = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID entityId = uuid(row.get("entity_id"));
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("entityType", "COMMUNITY");
            properties.put("entityId", entityId);
            properties.put("name", row.get("entity_name"));
            properties.put("boundaryStatus", row.get("boundary_status"));
            properties.put("freshness", "CURRENT");
            features.add(feature(entityId, row.get("geometry_json"), properties));
        }
        return featureCollection(features);
    }

    public Map<String, Object> listBuildingFeatures(
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
        if (!scope.global() && scope.communityIds().isEmpty()) {
            return featureCollection(List.of());
        }

        double tolerance = simplificationTolerance(zoom);
        MapSqlParameterSource params = viewportParams(west, south, east, north, tolerance);
        StringBuilder predicate = new StringBuilder();
        if (!scope.global()) {
            predicate.append(" AND b.community_id IN (:communityIds)");
            params.addValue("communityIds", scope.communityIds());
        }
        if (communityId != null) {
            predicate.append(" AND b.community_id=:communityId");
            params.addValue("communityId", communityId);
        }

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bb.building_id AS entity_id,
                       b.community_id,
                       b.building_name AS entity_name,
                       bb.status AS boundary_status,
                       risk.risk_level,
                       priority.priority_level,
                       ST_AsGeoJSON(
                           CASE WHEN :tolerance > 0
                                THEN ST_SimplifyPreserveTopology(bb.display_geometry, :tolerance)
                                ELSE bb.display_geometry END
                       ) AS geometry_json
                FROM geo.building_boundary bb
                JOIN core.building b ON b.id=bb.building_id AND b.deleted_at IS NULL
                LEFT JOIN LATERAL (
                    SELECT ra.risk_level
                    FROM core.risk_assessment ra
                    WHERE ra.building_id=b.id
                      AND ra.status IN ('CURRENT','CONFIRMED')
                    ORDER BY ra.assessed_at DESC
                    LIMIT 1
                ) risk ON TRUE
                LEFT JOIN LATERAL (
                    SELECT rp.priority_level
                    FROM core.renewal_priority rp
                    WHERE rp.building_id=b.id AND rp.status='CURRENT'
                    ORDER BY rp.generated_at DESC
                    LIMIT 1
                ) priority ON TRUE
                WHERE bb.deleted_at IS NULL
                  AND bb.status='VERIFIED'
                  AND bb.display_geometry IS NOT NULL
                  AND bb.display_geometry && ST_MakeEnvelope(:west, :south, :east, :north)
                """ + predicate + " ORDER BY b.building_name", params);

        List<Map<String, Object>> features = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID entityId = uuid(row.get("entity_id"));
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("entityType", "BUILDING");
            properties.put("entityId", entityId);
            properties.put("communityId", uuid(row.get("community_id")));
            properties.put("name", row.get("entity_name"));
            properties.put("boundaryStatus", row.get("boundary_status"));
            properties.put("riskLevel", row.get("risk_level"));
            properties.put("priorityLevel", row.get("priority_level"));
            properties.put("freshness", "CURRENT");
            features.add(feature(entityId, row.get("geometry_json"), properties));
        }
        return featureCollection(features);
    }

    private Map<String, Object> upsert(
            EntityType entityType, UUID entityId, Map<String, Object> request) {
        String sourceType = enumText(request.get("sourceType"), SOURCE_TYPES,
                "SPATIAL_SOURCE_TYPE_INVALID", "sourceType 不受支持");
        String sourceProvider = optionalEnumText(request.get("sourceProvider"), SOURCE_PROVIDERS,
                "SPATIAL_SOURCE_PROVIDER_INVALID", "sourceProvider 不受支持");
        String sourceCoordinateSystem = enumText(
                request.get("sourceCoordinateSystem"), COORDINATE_SYSTEMS,
                "SPATIAL_COORDINATE_SYSTEM_INVALID", "sourceCoordinateSystem 不受支持");
        GeometryPayload geometry = normalizeGeometry(request.get("geometry"), sourceCoordinateSystem);
        Integer expectedVersion = integer(request.get("expectedVersion"));
        String sourceObjectId = text(request.get("sourceObjectId"));
        String remark = text(request.get("remark"));

        Map<String, Object> current = findBoundary(entityType, entityId);
        int nextVersion;
        String action;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("entityId", entityId)
                .addValue("sourceType", sourceType)
                .addValue("sourceProvider", sourceProvider)
                .addValue("sourceObjectId", sourceObjectId)
                .addValue("sourceCoordinateSystem", sourceCoordinateSystem)
                .addValue("sourceGeometryJson", geometry.sourceJson())
                .addValue("displayGeometryJson", geometry.displayGcj02Json())
                .addValue("remark", remark);

        if (current == null) {
            if (expectedVersion != null && expectedVersion != 0) {
                throw versionConflict(expectedVersion, 0);
            }
            nextVersion = 1;
            action = "CREATE";
            params.addValue("version", nextVersion);
            jdbc.update(insertSql(entityType), params);
        } else {
            int currentVersion = ((Number) current.get("version")).intValue();
            if (expectedVersion == null || expectedVersion != currentVersion) {
                throw versionConflict(expectedVersion, currentVersion);
            }
            nextVersion = currentVersion + 1;
            action = "UPDATE";
            params.addValue("expectedVersion", currentVersion)
                    .addValue("nextVersion", nextVersion);
            int changed = jdbc.update(updateSql(entityType), params);
            if (changed != 1) {
                throw versionConflict(expectedVersion, currentVersion);
            }
        }

        Map<String, Object> saved = requireBoundary(entityType, entityId);
        appendRevision(entityType, entityId, saved, action);
        return saved;
    }

    private Map<String, Object> verify(
            EntityType entityType, UUID entityId, int expectedVersion, String remark) {
        Map<String, Object> current = requireBoundary(entityType, entityId);
        int currentVersion = ((Number) current.get("version")).intValue();
        if (expectedVersion != currentVersion) {
            throw versionConflict(expectedVersion, currentVersion);
        }
        if (current.get("geometry") == null) {
            throw new InvalidRequestException(
                    "SPATIAL_DISPLAY_GEOMETRY_MISSING", "当前边界没有可确认的 GCJ-02 展示几何");
        }

        UUID userId = CurrentUser.getUserId();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("entityId", entityId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("nextVersion", currentVersion + 1)
                .addValue("verifiedBy", userId)
                .addValue("remark", text(remark));
        int changed = jdbc.update(verifySql(entityType), params);
        if (changed != 1) {
            throw versionConflict(expectedVersion, currentVersion);
        }
        Map<String, Object> saved = requireBoundary(entityType, entityId);
        appendRevision(entityType, entityId, saved, "VERIFY");
        return saved;
    }

    private List<Map<String, Object>> revisions(EntityType entityType, UUID entityId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id AS revision_id, entity_type, entity_id, version, action,
                       snapshot::text AS snapshot_json, changed_by, created_at
                FROM geo.spatial_boundary_revision
                WHERE entity_type=:entityType AND entity_id=:entityId
                ORDER BY version DESC, created_at DESC
                """, Map.of("entityType", entityType.name(), "entityId", entityId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("revisionId", uuid(row.get("revision_id")));
            item.put("entityType", row.get("entity_type"));
            item.put("entityId", uuid(row.get("entity_id")));
            item.put("version", row.get("version"));
            item.put("action", row.get("action"));
            item.put("snapshot", parseJsonObject(row.get("snapshot_json")));
            item.put("changedBy", uuid(row.get("changed_by")));
            item.put("createdAt", row.get("created_at"));
            result.add(item);
        }
        return result;
    }

    private void appendRevision(
            EntityType entityType,
            UUID entityId,
            Map<String, Object> boundary,
            String action) {
        UUID changedBy = CurrentUser.getUserId();
        jdbc.update("""
                INSERT INTO geo.spatial_boundary_revision(
                    entity_type, entity_id, boundary_id, version, action,
                    snapshot, changed_by, created_at)
                VALUES (
                    :entityType, :entityId, :boundaryId, :version, :action,
                    CAST(:snapshot AS jsonb), :changedBy, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("entityType", entityType.name())
                .addValue("entityId", entityId)
                .addValue("boundaryId", boundary.get("boundaryId"))
                .addValue("version", boundary.get("version"))
                .addValue("action", action)
                .addValue("snapshot", writeJson(boundary))
                .addValue("changedBy", changedBy));
    }

    private Map<String, Object> requireBoundary(EntityType entityType, UUID entityId) {
        Map<String, Object> boundary = findBoundary(entityType, entityId);
        if (boundary == null) {
            throw new ResourceNotFoundException(
                    "SPATIAL_BOUNDARY_NOT_FOUND", "空间边界尚未维护");
        }
        return boundary;
    }

    private Map<String, Object> findBoundary(EntityType entityType, UUID entityId) {
        List<Map<String, Object>> rows = jdbc.queryForList(selectBoundarySql(entityType),
                Map.of("entityId", entityId));
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.getFirst();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("boundaryId", uuid(row.get("boundary_id")));
        result.put("entityType", entityType.name());
        result.put("entityId", entityId);
        result.put("sourceType", row.get("source_type"));
        result.put("sourceProvider", row.get("source_provider"));
        result.put("sourceObjectId", row.get("source_object_id"));
        result.put("sourceCoordinateSystem", row.get("source_coordinate_system"));
        result.put("displayCoordinateSystem", row.get("display_coordinate_system"));
        result.put("status", row.get("status"));
        result.put("version", row.get("version"));
        result.put("geometry", parseJsonObject(row.get("display_geometry_json")));
        result.put("verifiedBy", uuid(row.get("verified_by")));
        result.put("verifiedAt", row.get("verified_at"));
        result.put("remark", row.get("remark"));
        result.put("createdAt", row.get("created_at"));
        result.put("updatedAt", row.get("updated_at"));
        return result;
    }

    private String selectBoundarySql(EntityType entityType) {
        String table = entityType == EntityType.COMMUNITY
                ? "geo.community_boundary" : "geo.building_boundary";
        String idColumn = entityType == EntityType.COMMUNITY ? "community_id" : "building_id";
        return """
                SELECT id AS boundary_id,
                       source_type, source_provider, source_object_id,
                       source_coordinate_system, display_coordinate_system,
                       status, version,
                       ST_AsGeoJSON(display_geometry) AS display_geometry_json,
                       verified_by, verified_at, remark, created_at, updated_at
                FROM %s
                WHERE %s=:entityId AND deleted_at IS NULL
                """.formatted(table, idColumn);
    }

    private String insertSql(EntityType entityType) {
        if (entityType == EntityType.COMMUNITY) {
            return """
                    INSERT INTO geo.community_boundary(
                        community_id, source_type, source_provider, source_object_id,
                        source_coordinate_system, source_geometry_json,
                        display_coordinate_system, display_geometry,
                        status, version, remark, metadata, created_at, updated_at)
                    VALUES (
                        :entityId, :sourceType, :sourceProvider, :sourceObjectId,
                        :sourceCoordinateSystem, CAST(:sourceGeometryJson AS jsonb),
                        'GCJ02', ST_Multi(ST_GeomFromGeoJSON(:displayGeometryJson)),
                        'UNVERIFIED', :version, :remark, '{}'::jsonb,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;
        }
        return """
                INSERT INTO geo.building_boundary(
                    building_id, source_type, source_provider, source_object_id,
                    source_coordinate_system, source_geometry_json,
                    display_coordinate_system, display_geometry,
                    status, version, remark, created_at, updated_at)
                VALUES (
                    :entityId, :sourceType, :sourceProvider, :sourceObjectId,
                    :sourceCoordinateSystem, CAST(:sourceGeometryJson AS jsonb),
                    'GCJ02', ST_Multi(ST_GeomFromGeoJSON(:displayGeometryJson)),
                    'UNVERIFIED', :version, :remark,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
    }

    private String updateSql(EntityType entityType) {
        String table = entityType == EntityType.COMMUNITY
                ? "geo.community_boundary" : "geo.building_boundary";
        String idColumn = entityType == EntityType.COMMUNITY ? "community_id" : "building_id";
        return """
                UPDATE %s
                SET source_type=:sourceType,
                    source_provider=:sourceProvider,
                    source_object_id=:sourceObjectId,
                    source_coordinate_system=:sourceCoordinateSystem,
                    source_geometry_json=CAST(:sourceGeometryJson AS jsonb),
                    display_coordinate_system='GCJ02',
                    display_geometry=ST_Multi(ST_GeomFromGeoJSON(:displayGeometryJson)),
                    status='UNVERIFIED',
                    version=:nextVersion,
                    verified_by=NULL,
                    verified_at=NULL,
                    remark=:remark,
                    updated_at=CURRENT_TIMESTAMP
                WHERE %s=:entityId AND deleted_at IS NULL AND version=:expectedVersion
                """.formatted(table, idColumn);
    }

    private String verifySql(EntityType entityType) {
        String table = entityType == EntityType.COMMUNITY
                ? "geo.community_boundary" : "geo.building_boundary";
        String idColumn = entityType == EntityType.COMMUNITY ? "community_id" : "building_id";
        return """
                UPDATE %s
                SET status='VERIFIED',
                    version=:nextVersion,
                    verified_by=:verifiedBy,
                    verified_at=CURRENT_TIMESTAMP,
                    remark=COALESCE(:remark, remark),
                    updated_at=CURRENT_TIMESTAMP
                WHERE %s=:entityId AND deleted_at IS NULL AND version=:expectedVersion
                """.formatted(table, idColumn);
    }

    private Map<String, Object> feature(
            UUID entityId, Object geometryJson, Map<String, Object> properties) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", entityId);
        feature.put("geometry", parseJsonObject(geometryJson));
        feature.put("properties", properties);
        return feature;
    }

    private Map<String, Object> featureCollection(List<Map<String, Object>> features) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "FeatureCollection");
        result.put("features", features);
        return result;
    }

    private MapSqlParameterSource viewportParams(
            double west, double south, double east, double north, double tolerance) {
        return new MapSqlParameterSource()
                .addValue("west", west)
                .addValue("south", south)
                .addValue("east", east)
                .addValue("north", north)
                .addValue("tolerance", tolerance);
    }

    private void validateViewport(
            double west, double south, double east, double north, int zoom) {
        if (west < -180 || west > 180 || east < -180 || east > 180
                || south < -90 || south > 90 || north < -90 || north > 90
                || west >= east || south >= north) {
            throw new InvalidRequestException(
                    "SPATIAL_VIEWPORT_INVALID", "地图视野范围不合法");
        }
        if (zoom < 3 || zoom > 22) {
            throw new InvalidRequestException(
                    "SPATIAL_ZOOM_INVALID", "zoom 必须位于 3 到 22 之间");
        }
    }

    private double simplificationTolerance(int zoom) {
        if (zoom >= 18) {
            return 0D;
        }
        if (zoom >= 16) {
            return 0.000002D;
        }
        if (zoom >= 14) {
            return 0.00001D;
        }
        return 0.00005D;
    }

    private GeometryPayload normalizeGeometry(Object rawGeometry, String coordinateSystem) {
        if (rawGeometry == null) {
            throw new InvalidRequestException(
                    "SPATIAL_GEOMETRY_REQUIRED", "geometry 不能为空");
        }
        JsonNode geometry = objectMapper.valueToTree(rawGeometry);
        String type = geometry.path("type").asText();
        if (!"Polygon".equals(type) && !"MultiPolygon".equals(type)) {
            throw new InvalidRequestException(
                    "SPATIAL_GEOMETRY_TYPE_INVALID", "geometry 仅支持 Polygon 或 MultiPolygon");
        }
        JsonNode coordinates = geometry.path("coordinates");
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            throw new InvalidRequestException(
                    "SPATIAL_GEOMETRY_COORDINATES_INVALID", "geometry.coordinates 不能为空");
        }
        String sourceJson = writeJson(geometry);
        JsonNode display = "WGS84".equals(coordinateSystem)
                ? wgs84GeometryToGcj02(geometry) : geometry.deepCopy();
        return new GeometryPayload(sourceJson, writeJson(display));
    }

    private JsonNode wgs84GeometryToGcj02(JsonNode geometry) {
        ObjectNode converted = objectMapper.createObjectNode();
        converted.put("type", geometry.path("type").asText());
        converted.set("coordinates", transformCoordinateNode(geometry.path("coordinates")));
        return converted;
    }

    private JsonNode transformCoordinateNode(JsonNode node) {
        if (!node.isArray()) {
            throw new InvalidRequestException(
                    "SPATIAL_GEOMETRY_COORDINATES_INVALID", "GeoJSON 坐标结构不合法");
        }
        ArrayNode array = (ArrayNode) node;
        if (array.size() >= 2 && array.get(0).isNumber() && array.get(1).isNumber()) {
            double[] point = wgs84ToGcj02(array.get(0).asDouble(), array.get(1).asDouble());
            ArrayNode result = objectMapper.createArrayNode();
            result.add(point[0]);
            result.add(point[1]);
            for (int index = 2; index < array.size(); index++) {
                result.add(array.get(index));
            }
            return result;
        }
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode child : array) {
            result.add(transformCoordinateNode(child));
        }
        return result;
    }

    /** 标准 WGS84 → GCJ-02 偏移算法；中国大陆范围外保持原坐标。 */
    private double[] wgs84ToGcj02(double longitude, double latitude) {
        if (outOfChina(longitude, latitude)) {
            return new double[]{longitude, latitude};
        }
        double dLat = transformLat(longitude - 105.0, latitude - 35.0);
        double dLon = transformLon(longitude - 105.0, latitude - 35.0);
        double radLat = latitude / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - 0.00669342162296594323 * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0)
                / ((6378245.0 * (1 - 0.00669342162296594323))
                / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0)
                / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{longitude + dLon, latitude + dLat};
    }

    private boolean outOfChina(double longitude, double latitude) {
        return longitude < 72.004 || longitude > 137.8347
                || latitude < 0.8293 || latitude > 55.8271;
    }

    private double transformLat(double x, double y) {
        double result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y
                + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * Math.PI)
                + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(y * Math.PI)
                + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        result += (160.0 * Math.sin(y / 12.0 * Math.PI)
                + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return result;
    }

    private double transformLon(double x, double y) {
        double result = 300.0 + x + 2.0 * y + 0.1 * x * x
                + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * Math.PI)
                + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(x * Math.PI)
                + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        result += (150.0 * Math.sin(x / 12.0 * Math.PI)
                + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return result;
    }

    private String enumText(Object value, Set<String> allowed, String code, String message) {
        String text = text(value);
        if (text == null) {
            throw new InvalidRequestException(code, message);
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(code, message);
        }
        return normalized;
    }

    private String optionalEnumText(
            Object value, Set<String> allowed, String code, String message) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new InvalidRequestException(code, message);
        }
        return normalized;
    }

    private ResourceConflictException versionConflict(Integer expected, int actual) {
        String expectedText = expected == null ? "未提供" : String.valueOf(expected);
        return new ResourceConflictException(
                "SPATIAL_BOUNDARY_VERSION_CONFLICT",
                "边界已被其他人员修改，请刷新后重试（expectedVersion="
                        + expectedText + ", currentVersion=" + actual + "）");
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException(
                    "SPATIAL_VERSION_INVALID", "expectedVersion 必须为非负整数");
        }
    }

    private UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        return UUID.fromString(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Map.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("数据库中的 GeoJSON/JSON 快照无法解析", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException(
                    "SPATIAL_JSON_INVALID", "空间边界 JSON 无法序列化");
        }
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank()
                ? null : String.valueOf(value).trim();
    }

    private enum EntityType {
        COMMUNITY,
        BUILDING
    }

    private record GeometryPayload(String sourceJson, String displayGcj02Json) {
    }
}
