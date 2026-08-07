package org.urbansafe.priority.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 受限小区和楼栋列表必须在真实 PostgreSQL 查询阶段应用授权范围。 */
class ScopedArchiveQueryServiceIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private ScopedArchiveQueryService queryService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private UUID allowedCommunityId;
    private UUID otherCommunityId;
    private UUID allowedBuildingId;

    @BeforeEach
    void setUp() {
        allowedCommunityId = UUID.randomUUID();
        otherCommunityId = UUID.randomUUID();
        allowedBuildingId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        insertCommunity(allowedCommunityId, "QA-" + suffix, "授权小区");
        insertCommunity(otherCommunityId, "QB-" + suffix, "其他小区");
        insertBuilding(allowedBuildingId, allowedCommunityId, "BA-" + suffix, "授权楼栋");
        insertBuilding(UUID.randomUUID(), otherCommunityId, "BB-" + suffix, "其他楼栋");
    }

    @Test
    void restrictedCommunityPageShouldOnlyCountAuthorizedIds() {
        CommunityAccessScope scope = CommunityAccessScope.restricted(Set.of(allowedCommunityId));

        var page = queryService.listCommunities(
                null, null, null, new ApiPageRequest(0, 20), "createdAt,desc", scope);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.content()).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(allowedCommunityId);
                    assertThat(row.communityName()).isEqualTo("授权小区");
                });
    }

    @Test
    void restrictedBuildingPageShouldOnlyCountAuthorizedCommunity() {
        CommunityAccessScope scope = CommunityAccessScope.restricted(Set.of(allowedCommunityId));

        var page = queryService.listBuildings(
                null, null, new ApiPageRequest(0, 20), "createdAt,desc", scope);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(allowedBuildingId);
                    assertThat(row.communityId()).isEqualTo(allowedCommunityId);
                });
    }

    private void insertCommunity(UUID id, String code, String name) {
        jdbc.update("""
                INSERT INTO core.community(id, community_code, community_name,
                    administrative_region, address, building_count,
                    household_count, resident_count, status)
                VALUES (:id, :code, :name, '测试区', '测试地址', 1, 10, 30, 'ACTIVE')
                """, Map.of("id", id, "code", code, "name", name));
    }

    private void insertBuilding(UUID id, UUID communityId, String code, String name) {
        jdbc.update("""
                INSERT INTO core.building(id, community_id, building_code, building_name,
                    construction_year, floor_count, resident_count, status)
                VALUES (:id, :communityId, :code, :name, 2000, 6, 30, 'ACTIVE')
                """, Map.of(
                "id", id,
                "communityId", communityId,
                "code", code,
                "name", name));
    }
}
