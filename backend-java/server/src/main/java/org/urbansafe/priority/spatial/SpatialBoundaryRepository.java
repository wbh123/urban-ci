package org.urbansafe.priority.spatial;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.urbansafe.priority.common.security.CommunityAccessScope;

/**
 * PostGIS 空间边界仓储。
 *
 * <p>表名和实体外键列只能来自 {@link BoundaryEntityType}，客户端输入永远不会进入 SQL 标识符。
 * 展示几何统一以 SRID 0 保存，真实坐标含义由 display_coordinate_system 显式表达，避免把 GCJ-02
 * 错当成 EPSG:4326。
 */
@Repository
public class SpatialBoundaryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SpatialBoundaryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SpatialBoundarySnapshot> findCurrent(BoundaryEntityType type, UUID entityId) {
        String sql = """
                SELECT id,
                       %s AS entity_id,
                       source_type,
                       source_provider,
                       source_object_id,
                       source_coordinate_system,
                       source_geometry_json::text AS source_geometry_json_text,
                       display_coordinate_system,
                       ST_AsGeoJSON(display_geometry) AS display_geometry_json,
                       status,
                       version,
                       verified_by,
                       verified_at,
                       remark,
                       created_at,
                       updated_at
                FROM %s
                WHERE %s=:entityId AND deleted_at IS NULL
                """.formatted(type.entityColumn(), type.tableName(), type.entityColumn());
        List<SpatialBoundarySnapshot> rows = jdbc.query(
                sql,
                Map.of("entityId", entityId),
                (rs, rowNum) -> mapSnapshot(rs, type));
        return rows.stream().findFirst();
    }

    public SpatialBoundarySnapshot insertUnverified(
            BoundaryEntityType type,
            UUID entityId,
            SpatialBoundaryWriteCommand command,
            long nextVersion) {
        String sql = """
                INSERT INTO %s (
                    %s,
                    source_type,
                    source_provider,
                    source_object_id,
                    source_coordinate_system,
                    source_geometry_json,
                    display_coordinate_system,
                    display_geometry,
                    status,
                    version,
                    verified_by,
                    verified_at,
                    remark)
                VALUES (
                    :entityId,
                    :sourceType,
                    :sourceProvider,
                    :sourceObjectId,
                    :sourceCoordinateSystem,
                    CAST(:sourceGeometryJson AS jsonb),
                    :displayCoordinateSystem,
                    ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:displayGeometryJson), 0)),
                    'UNVERIFIED',
                    :nextVersion,
                    NULL,
                    NULL,
                    :remark)
                RETURNING id,
                          %s AS entity_id,
                          source_type,
                          source_provider,
                          source_object_id,
                          source_coordinate_system,
                          source_geometry_json::text AS source_geometry_json_text,
                          display_coordinate_system,
                          ST_AsGeoJSON(display_geometry) AS display_geometry_json,
                          status,
                          version,
                          verified_by,
                          verified_at,
                          remark,
                          created_at,
                          updated_at
                """.formatted(type.tableName(), type.entityColumn(), type.entityColumn());
        return jdbc.queryForObject(sql, writeParams(entityId, command, nextVersion),
                (rs, rowNum) -> mapSnapshot(rs, type));
    }

    public Optional<SpatialBoundarySnapshot> updateUnverified(
            BoundaryEntityType type,
            UUID entityId,
            SpatialBoundaryWriteCommand command,
            long expectedVersion,
            long nextVersion) {
        String sql = """
                UPDATE %s
                SET source_type=:sourceType,
                    source_provider=:sourceProvider,
                    source_object_id=:sourceObjectId,
                    source_coordinate_system=:sourceCoordinateSystem,
                    source_geometry_json=CAST(:sourceGeometryJson AS jsonb),
                    display_coordinate_system=:displayCoordinateSystem,
                    display_geometry=ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:displayGeometryJson), 0)),
                    status='UNVERIFIED',
                    version=:nextVersion,
                    verified_by=NULL,
                    verified_at=NULL,
                    remark=:remark,
                    updated_at=CURRENT_TIMESTAMP
                WHERE %s=:entityId
                  AND deleted_at IS NULL
                  AND version=:expectedVersion
                RETURNING id,
                          %s AS entity_id,
                          source_type,
                          source_provider,
                          source_object_id,
                          source_coordinate_system,
                          source_geometry_json::text AS source_geometry_json_text,
                          display_coordinate_system,
                          ST_AsGeoJSON(display_geometry) AS display_geometry_json,
                          status,
                          version,
                          verified_by,
                          verified_at,
                          remark,
                          created_at,
                          updated_at
                """.formatted(type.tableName(), type.entityColumn(), type.entityColumn());
        MapSqlParameterSource params = writeParams(entityId, command, nextVersion)
                .addValue("expectedVersion", expectedVersion);
        List<SpatialBoundarySnapshot> rows = jdbc.query(
                sql, params, (rs, rowNum) -> mapSnapshot(rs, type));
        return rows.stream().findFirst();
    }

    public Optional<SpatialBoundarySnapshot> transitionStatus(
            BoundaryEntityType type,
            UUID entityId,
            long expectedVersion,
            long nextVersion,
            BoundaryStatus targetStatus,
            UUID actorId,
            String remark) {
        String sql = """
                UPDATE %s
                SET status=:targetStatus,
                    version=:nextVersion,
                    verified_by=CASE
                        WHEN :targetStatus='VERIFIED' THEN CAST(:actorId AS uuid)
                        ELSE NULL::uuid
                    END,
                    verified_at=CASE WHEN :targetStatus='VERIFIED' THEN CURRENT_TIMESTAMP ELSE NULL END,
                    remark=:remark,
                    updated_at=CURRENT_TIMESTAMP
                WHERE %s=:entityId
                  AND deleted_at IS NULL
                  AND version=:expectedVersion
                RETURNING id,
                          %s AS entity_id,
                          source_type,
                          source_provider,
                          source_object_id,
                          source_coordinate_system,
                          source_geometry_json::text AS source_geometry_json_text,
                          display_coordinate_system,
                          ST_AsGeoJSON(display_geometry) AS display_geometry_json,
                          status,
                          version,
                          verified_by,
                          verified_at,
                          remark,
                          created_at,
                          updated_at
                """.formatted(type.tableName(), type.entityColumn(), type.entityColumn());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("entityId", entityId)
                .addValue("expectedVersion", expectedVersion)
                .addValue("nextVersion", nextVersion)
                .addValue("targetStatus", targetStatus.name())
                .addValue("actorId", actorId)
                .addValue("remark", remark);
        List<SpatialBoundarySnapshot> rows = jdbc.query(
                sql, params, (rs, rowNum) -> mapSnapshot(rs, type));
        return rows.stream().findFirst();
    }

    /** 正式地图查询只返回 VERIFIED 小区边界，并在数据库侧完成 bbox、辖区和 zoom 简化。 */
    public List<SpatialMapFeature> queryVerifiedCommunities(
            double west,
            double south,
            double east,
            double north,
            double tolerance,
            CommunityAccessScope scope) {
        if (!scope.global() && scope.communityIds().isEmpty()) {
            return List.of();
        }
        String scopePredicate = scope.global() ? "" : " AND cb.community_id IN (:communityIds)";
        String sql = """
                SELECT cb.community_id AS entity_id,
                       c.community_code AS entity_code,
                       c.community_name AS entity_name,
                       cb.community_id AS community_id,
                       cb.display_coordinate_system,
                       cb.status,
                       cb.version,
                       cb.source_type,
                       ST_AsGeoJSON(
                           CASE WHEN :tolerance <= 0.0
                                THEN cb.display_geometry
                                ELSE ST_SimplifyPreserveTopology(cb.display_geometry, :tolerance)
                           END
                       ) AS geometry_json
                FROM geo.community_boundary cb
                JOIN core.community c ON c.id=cb.community_id AND c.deleted_at IS NULL
                WHERE cb.deleted_at IS NULL
                  AND cb.status='VERIFIED'
                  AND cb.display_geometry IS NOT NULL
                  AND cb.display_geometry && ST_MakeEnvelope(:west, :south, :east, :north, 0)
                """ + scopePredicate + " ORDER BY c.community_code, cb.community_id";
        MapSqlParameterSource params = bboxParams(west, south, east, north, tolerance);
        if (!scope.global()) {
            params.addValue("communityIds", scope.communityIds());
        }
        return jdbc.query(sql, params, (rs, rowNum) -> mapFeature(rs, BoundaryEntityType.COMMUNITY));
    }

    /** 正式地图查询只返回 VERIFIED 楼栋边界，可附加小区过滤并复用当前用户辖区范围。 */
    public List<SpatialMapFeature> queryVerifiedBuildings(
            double west,
            double south,
            double east,
            double north,
            double tolerance,
            UUID communityId,
            CommunityAccessScope scope) {
        if (!scope.global() && scope.communityIds().isEmpty()) {
            return List.of();
        }
        StringBuilder predicate = new StringBuilder();
        if (communityId != null) {
            predicate.append(" AND b.community_id=:communityId");
        }
        if (!scope.global()) {
            predicate.append(" AND b.community_id IN (:communityIds)");
        }
        String sql = """
                SELECT bb.building_id AS entity_id,
                       b.building_code AS entity_code,
                       b.building_name AS entity_name,
                       b.community_id,
                       bb.display_coordinate_system,
                       bb.status,
                       bb.version,
                       bb.source_type,
                       ST_AsGeoJSON(
                           CASE WHEN :tolerance <= 0.0
                                THEN bb.display_geometry
                                ELSE ST_SimplifyPreserveTopology(bb.display_geometry, :tolerance)
                           END
                       ) AS geometry_json
                FROM geo.building_boundary bb
                JOIN core.building b ON b.id=bb.building_id AND b.deleted_at IS NULL
                JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
                WHERE bb.deleted_at IS NULL
                  AND bb.status='VERIFIED'
                  AND bb.display_geometry IS NOT NULL
                  AND bb.display_geometry && ST_MakeEnvelope(:west, :south, :east, :north, 0)
                """ + predicate + " ORDER BY b.building_code, bb.building_id";
        MapSqlParameterSource params = bboxParams(west, south, east, north, tolerance);
        if (communityId != null) {
            params.addValue("communityId", communityId);
        }
        if (!scope.global()) {
            params.addValue("communityIds", scope.communityIds());
        }
        return jdbc.query(sql, params, (rs, rowNum) -> mapFeature(rs, BoundaryEntityType.BUILDING));
    }

    /** 保存不可变版本快照；调用方必须与主表更新处于同一事务。 */
    public void appendRevision(
            SpatialBoundarySnapshot snapshot,
            BoundaryChangeType changeType,
            UUID actorId) {
        jdbc.update("""
                INSERT INTO geo.spatial_boundary_revision (
                    entity_type,
                    entity_id,
                    boundary_id,
                    version,
                    source_type,
                    source_provider,
                    source_object_id,
                    source_coordinate_system,
                    source_geometry_json,
                    display_coordinate_system,
                    display_geometry,
                    status,
                    change_type,
                    remark,
                    changed_by,
                    changed_at)
                VALUES (
                    :entityType,
                    :entityId,
                    :boundaryId,
                    :version,
                    :sourceType,
                    :sourceProvider,
                    :sourceObjectId,
                    :sourceCoordinateSystem,
                    CAST(:sourceGeometryJson AS jsonb),
                    :displayCoordinateSystem,
                    ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:displayGeometryJson), 0)),
                    :status,
                    :changeType,
                    :remark,
                    CAST(:actorId AS uuid),
                    CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("entityType", snapshot.entityType().name())
                .addValue("entityId", snapshot.entityId())
                .addValue("boundaryId", snapshot.id())
                .addValue("version", snapshot.version())
                .addValue("sourceType", snapshot.sourceType())
                .addValue("sourceProvider", snapshot.sourceProvider())
                .addValue("sourceObjectId", snapshot.sourceObjectId())
                .addValue("sourceCoordinateSystem", snapshot.sourceCoordinateSystem())
                .addValue("sourceGeometryJson", snapshot.sourceGeometryJson())
                .addValue("displayCoordinateSystem", snapshot.displayCoordinateSystem())
                .addValue("displayGeometryJson", snapshot.displayGeometryJson())
                .addValue("status", snapshot.status().name())
                .addValue("changeType", changeType.name())
                .addValue("remark", snapshot.remark())
                .addValue("actorId", actorId));
    }

    private MapSqlParameterSource bboxParams(
            double west,
            double south,
            double east,
            double north,
            double tolerance) {
        return new MapSqlParameterSource()
                .addValue("west", west)
                .addValue("south", south)
                .addValue("east", east)
                .addValue("north", north)
                .addValue("tolerance", tolerance);
    }

    private MapSqlParameterSource writeParams(
            UUID entityId,
            SpatialBoundaryWriteCommand command,
            long nextVersion) {
        return new MapSqlParameterSource()
                .addValue("entityId", entityId)
                .addValue("sourceType", command.sourceType())
                .addValue("sourceProvider", command.sourceProvider())
                .addValue("sourceObjectId", command.sourceObjectId())
                .addValue("sourceCoordinateSystem", command.sourceCoordinateSystem())
                .addValue("sourceGeometryJson", command.sourceGeometryJson())
                .addValue("displayCoordinateSystem", command.displayCoordinateSystem())
                .addValue("displayGeometryJson", command.displayGeometryJson())
                .addValue("nextVersion", nextVersion)
                .addValue("remark", command.remark());
    }

    private SpatialMapFeature mapFeature(ResultSet rs, BoundaryEntityType type) throws SQLException {
        return new SpatialMapFeature(
                rs.getObject("entity_id", UUID.class),
                type,
                rs.getString("entity_code"),
                rs.getString("entity_name"),
                rs.getObject("community_id", UUID.class),
                rs.getString("display_coordinate_system"),
                BoundaryStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getString("source_type"),
                rs.getString("geometry_json"));
    }

    private SpatialBoundarySnapshot mapSnapshot(ResultSet rs, BoundaryEntityType type) throws SQLException {
        return new SpatialBoundarySnapshot(
                rs.getObject("id", UUID.class),
                type,
                rs.getObject("entity_id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_provider"),
                rs.getString("source_object_id"),
                rs.getString("source_coordinate_system"),
                rs.getString("source_geometry_json_text"),
                rs.getString("display_coordinate_system"),
                rs.getString("display_geometry_json"),
                BoundaryStatus.valueOf(rs.getString("status")),
                rs.getLong("version"),
                rs.getObject("verified_by", UUID.class),
                rs.getObject("verified_at", OffsetDateTime.class),
                rs.getString("remark"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
