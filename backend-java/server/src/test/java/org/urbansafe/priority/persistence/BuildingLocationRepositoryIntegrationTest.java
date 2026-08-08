package org.urbansafe.priority.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.phase2.repository.Phase2Repository;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 楼栋中心点必须通过 Phase2Repository 以单活跃记录语义新增或更新。 */
class BuildingLocationRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private Phase2Repository repository;

    @Test
    void saveBuildingLocationUpsertsSingleActiveRecordAndCanReadItBack() throws Exception {
        UUID buildingId = createBuilding();
        Method save = Phase2Repository.class.getMethod(
                "saveBuildingLocation",
                UUID.class,
                double.class,
                double.class,
                String.class,
                String.class,
                String.class,
                String.class);
        Method find = Phase2Repository.class.getMethod("findBuildingLocation", UUID.class);

        save.invoke(repository, buildingId, 113.12, 27.88, "示范路1号", "MANUAL", "MANUAL", "{}");
        save.invoke(repository, buildingId, 113.13, 27.89, "示范路2号", "AMAP", "门牌号", "{\"sourceMode\":\"POI_SEARCH\"}");

        @SuppressWarnings("unchecked")
        Optional<Map<String, Object>> stored = (Optional<Map<String, Object>>) find.invoke(repository, buildingId);
        Integer activeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM geo.building_location
                WHERE building_id=? AND deleted_at IS NULL
                """, Integer.class, buildingId);

        assertThat(activeCount).isEqualTo(1);
        assertThat(stored).isPresent();
        assertThat(stored.orElseThrow())
                .containsEntry("buildingId", buildingId)
                .containsEntry("longitude", 113.13)
                .containsEntry("latitude", 27.89)
                .containsEntry("formattedAddress", "示范路2号")
                .containsEntry("provider", "AMAP")
                .containsEntry("coordinateSystem", "GCJ02")
                .containsEntry("matchLevel", "门牌号");
    }

    private UUID createBuilding() {
        CommunityEntity community = new CommunityEntity();
        community.setId(UUID.randomUUID());
        community.setCommunityCode("BL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        community.setCommunityName("楼栋定位测试小区");
        community.setBuildingCount(0);
        community.setHouseholdCount(0);
        community.setResidentCount(0);
        community.setStatus("ACTIVE");
        community.setExtraAttributes(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        community.setVersion(0L);
        communityMapper.insert(community);

        UUID buildingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, '定位测试楼', '示范路1号', 2000, 'BRICK_CONCRETE',
                        6, 1200, 10, 30, 'ACTIVE', '{}'::jsonb)
                """,
                buildingId,
                community.getId(),
                "B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return buildingId;
    }
}
