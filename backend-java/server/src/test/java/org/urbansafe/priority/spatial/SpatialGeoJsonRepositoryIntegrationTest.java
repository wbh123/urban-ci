package org.urbansafe.priority.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.common.security.CommunityAccessScope;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 真实 PostGIS 查询必须同时执行 VERIFIED、bbox 和辖区过滤。 */
class SpatialGeoJsonRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private SpatialBoundaryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void communityQueryReturnsOnlyVerifiedFeaturesInsideBboxAndScope() {
        UUID allowed = createCommunity("R2-SP-ALLOW");
        UUID unverified = createCommunity("R2-SP-DRAFT");
        UUID outside = createCommunity("R2-SP-OUT");

        createBoundary(BoundaryEntityType.COMMUNITY, allowed, polygon(113.00, 27.00), true);
        createBoundary(BoundaryEntityType.COMMUNITY, unverified, polygon(113.02, 27.02), false);
        createBoundary(BoundaryEntityType.COMMUNITY, outside, polygon(116.00, 30.00), true);

        List<SpatialMapFeature> rows = repository.queryVerifiedCommunities(
                112.9, 26.9, 114.0, 28.0, 0.0,
                CommunityAccessScope.restricted(Set.of(allowed, unverified)));

        assertThat(rows).extracting(SpatialMapFeature::entityId).containsExactly(allowed);
        assertThat(rows.getFirst().status()).isEqualTo(BoundaryStatus.VERIFIED);
        assertThat(rows.getFirst().geometryJson()).contains("MultiPolygon");
    }

    @Test
    void buildingQueryHonorsCommunityFilterAndScope() {
        UUID firstCommunity = createCommunity("R2-SP-C1");
        UUID secondCommunity = createCommunity("R2-SP-C2");
        UUID firstBuilding = createBuilding(firstCommunity, "R2-SP-B1");
        UUID secondBuilding = createBuilding(secondCommunity, "R2-SP-B2");
        createBoundary(BoundaryEntityType.BUILDING, firstBuilding, polygon(113.10, 27.10), true);
        createBoundary(BoundaryEntityType.BUILDING, secondBuilding, polygon(113.20, 27.20), true);

        List<SpatialMapFeature> rows = repository.queryVerifiedBuildings(
                113.0, 27.0, 114.0, 28.0, 0.00008, firstCommunity,
                CommunityAccessScope.restricted(Set.of(firstCommunity, secondCommunity)));

        assertThat(rows).extracting(SpatialMapFeature::entityId).containsExactly(firstBuilding);
        assertThat(rows.getFirst().communityId()).isEqualTo(firstCommunity);
    }

    private void createBoundary(
            BoundaryEntityType type,
            UUID entityId,
            String geometry,
            boolean verified) {
        SpatialBoundaryWriteCommand command = new SpatialBoundaryWriteCommand(
                0L,
                "MANUAL_DRAW",
                "POSTGIS_TEST",
                null,
                "GCJ02",
                geometry,
                "GCJ02",
                geometry,
                "bbox test");
        SpatialBoundarySnapshot created = repository.insertUnverified(type, entityId, command, 1L);
        if (verified) {
            repository.transitionStatus(type, entityId, 1L, 2L, BoundaryStatus.VERIFIED, null, "verified")
                    .orElseThrow();
        }
    }

    private UUID createCommunity(String codePrefix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.community
                  (id, community_code, community_name, building_count, household_count,
                   resident_count, status, extra_attributes)
                VALUES (?, ?, ?, 0, 0, 0, 'ACTIVE', '{}'::jsonb)
                """, id, codePrefix + "-" + id.toString().substring(0, 8), codePrefix + "小区");
        return id;
    }

    private UUID createBuilding(UUID communityId, String codePrefix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, ?, '测试地址', 2000, 'FRAME', 6, 1000, 10, 20,
                        'ACTIVE', '{}'::jsonb)
                """, id, communityId, codePrefix + "-" + id.toString().substring(0, 8), codePrefix + "楼");
        return id;
    }

    private String polygon(double west, double south) {
        double east = west + 0.005;
        double north = south + 0.005;
        return "{\"type\":\"Polygon\",\"coordinates\":[[["
                + west + "," + south + "],[" + east + "," + south + "],["
                + east + "," + north + "],[" + west + "," + north + "],["
                + west + "," + south + "]]]}";
    }
}
