package org.urbansafe.priority.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 使用真实 PostgreSQL/PostGIS 验证边界几何、版本条件更新和 revision 快照。 */
class SpatialBoundaryRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private SpatialBoundaryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void communityBoundaryPersistsAsMultiPolygonAndKeepsImmutableRevisions() {
        UUID communityId = createCommunity();
        SpatialBoundarySnapshot created = repository.insertUnverified(
                BoundaryEntityType.COMMUNITY, communityId, command(0L, "首次录入"), 1L);
        repository.appendRevision(created, BoundaryChangeType.UPSERT, null);

        assertThat(created.version()).isEqualTo(1L);
        assertThat(created.status()).isEqualTo(BoundaryStatus.UNVERIFIED);
        assertThat(created.displayGeometryJson()).contains("MultiPolygon");

        SpatialBoundarySnapshot updated = repository.updateUnverified(
                        BoundaryEntityType.COMMUNITY,
                        communityId,
                        command(1L, "二次调整"),
                        1L,
                        2L)
                .orElseThrow();
        repository.appendRevision(updated, BoundaryChangeType.UPSERT, null);

        SpatialBoundarySnapshot verified = repository.transitionStatus(
                        BoundaryEntityType.COMMUNITY,
                        communityId,
                        2L,
                        3L,
                        BoundaryStatus.VERIFIED,
                        null,
                        "人工确认")
                .orElseThrow();
        repository.appendRevision(verified, BoundaryChangeType.VERIFY, null);

        assertThat(verified.status()).isEqualTo(BoundaryStatus.VERIFIED);
        assertThat(verified.version()).isEqualTo(3L);
        Integer revisionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM geo.spatial_boundary_revision
                WHERE entity_type='COMMUNITY' AND entity_id=?
                """, Integer.class, communityId);
        assertThat(revisionCount).isEqualTo(3);

        Integer versionsAreUnique = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT version)
                FROM geo.spatial_boundary_revision
                WHERE entity_type='COMMUNITY' AND entity_id=?
                """, Integer.class, communityId);
        assertThat(versionsAreUnique).isEqualTo(3);
    }

    @Test
    void staleBuildingBoundaryUpdateAffectsNoRows() {
        UUID communityId = createCommunity();
        UUID buildingId = createBuilding(communityId);
        SpatialBoundarySnapshot created = repository.insertUnverified(
                BoundaryEntityType.BUILDING, buildingId, command(0L, "楼栋边界"), 1L);

        SpatialBoundarySnapshot updated = repository.updateUnverified(
                        BoundaryEntityType.BUILDING,
                        buildingId,
                        command(1L, "首次更新"),
                        1L,
                        2L)
                .orElseThrow();

        assertThat(updated.version()).isEqualTo(2L);
        assertThat(repository.updateUnverified(
                BoundaryEntityType.BUILDING,
                buildingId,
                command(1L, "过期写入"),
                1L,
                2L)).isEmpty();
    }

    private UUID createCommunity() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.community
                  (id, community_code, community_name, building_count, household_count,
                   resident_count, status, extra_attributes)
                VALUES (?, ?, 'R2 空间测试小区', 0, 0, 0, 'ACTIVE', '{}'::jsonb)
                """, id, "R2-C-" + id.toString().substring(0, 8));
        return id;
    }

    private UUID createBuilding(UUID communityId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, 'R2 空间测试楼', '测试路 1 号', 2000, 'FRAME',
                        6, 1200, 10, 20, 'ACTIVE', '{}'::jsonb)
                """, id, communityId, "R2-B-" + id.toString().substring(0, 8));
        return id;
    }

    private SpatialBoundaryWriteCommand command(long expectedVersion, String remark) {
        String geometry = """
                {"type":"Polygon","coordinates":[[[113.0000,27.0000],[113.0020,27.0000],
                [113.0020,27.0020],[113.0000,27.0020],[113.0000,27.0000]]]}
                """.replace("\n", "").trim();
        return new SpatialBoundaryWriteCommand(
                expectedVersion,
                "MANUAL_DRAW",
                "INTEGRATION_TEST",
                null,
                "GCJ02",
                geometry,
                "GCJ02",
                geometry,
                remark);
    }
}
