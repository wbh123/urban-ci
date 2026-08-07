package org.urbansafe.priority.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 小区和楼栋通用数据范围的真实 PostgreSQL 测试。 */
class BusinessAccessServiceIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private BusinessAccessService accessService;

    private UUID allowedCommunityId;
    private UUID otherCommunityId;
    private UUID allowedBuildingId;
    private UUID otherBuildingId;

    @BeforeEach
    void setUp() {
        allowedCommunityId = UUID.randomUUID();
        otherCommunityId = UUID.randomUUID();
        allowedBuildingId = UUID.randomUUID();
        otherBuildingId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        insertCommunity(allowedCommunityId, "SCOPE-A-" + suffix);
        insertCommunity(otherCommunityId, "SCOPE-B-" + suffix);
        insertBuilding(allowedBuildingId, allowedCommunityId, "SCOPE-A-B-" + suffix);
        insertBuilding(otherBuildingId, otherCommunityId, "SCOPE-B-B-" + suffix);
        insertScopedUser("scope-manager", allowedCommunityId);
    }

    @Test
    @WithMockUser(username = "scope-manager", roles = "COMMUNITY_MANAGER")
    void communityManagerShouldOnlyAccessAuthorizedCommunityAndBuilding() {
        CommunityAccessScope scope = accessService.currentCommunityScope();

        assertThat(scope.global()).isFalse();
        assertThat(scope.communityIds()).containsExactly(allowedCommunityId);
        assertThatCode(() -> accessService.assertCanReadCommunity(allowedCommunityId))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.assertCanManageCommunity(allowedCommunityId))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.assertCanCreateBuilding(allowedCommunityId))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.assertCanReadBuilding(allowedBuildingId))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.assertCanManageBuilding(allowedBuildingId))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> accessService.assertCanReadCommunity(otherCommunityId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accessService.assertCanManageCommunity(otherCommunityId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accessService.assertCanReadBuilding(otherBuildingId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accessService.assertCanMoveBuilding(
                allowedBuildingId, otherCommunityId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(accessService::assertCanCreateCommunity)
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accessService.assertCanDeleteCommunity(allowedCommunityId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "government", roles = "GOVERNMENT_MANAGER")
    void governmentManagerShouldHaveGlobalReadAndManagementScope() {
        CommunityAccessScope scope = accessService.currentCommunityScope();

        assertThat(scope.global()).isTrue();
        assertThatCode(accessService::assertCanCreateCommunity).doesNotThrowAnyException();
        assertThatCode(() -> accessService.assertCanDeleteCommunity(otherCommunityId))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessService.assertCanManageBuilding(otherBuildingId))
                .doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(username = "expert", roles = "EXPERT")
    void expertShouldReadGloballyButNotModifyBaseArchives() {
        assertThat(accessService.currentCommunityScope().global()).isTrue();
        assertThatCode(() -> accessService.assertCanReadBuilding(otherBuildingId))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.assertCanManageBuilding(otherBuildingId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "field", roles = "PROPERTY_INSPECTOR")
    void fieldRoleShouldNotReceiveGenericArchiveDirectoryScope() {
        assertThatThrownBy(accessService::currentCommunityScope)
                .isInstanceOf(AccessDeniedException.class);
    }

    private void insertCommunity(UUID id, String code) {
        jdbc.update("""
                INSERT INTO core.community(id, community_code, community_name)
                VALUES (:id, :code, :name)
                """, Map.of("id", id, "code", code, "name", code + "小区"));
    }

    private void insertBuilding(UUID id, UUID communityId, String code) {
        jdbc.update("""
                INSERT INTO core.building(id, community_id, building_code, building_name)
                VALUES (:id, :communityId, :code, :name)
                """, Map.of(
                "id", id,
                "communityId", communityId,
                "code", code,
                "name", code + "楼栋"));
    }

    private void insertScopedUser(String username, UUID communityId) {
        jdbc.update("""
                INSERT INTO core.user_account(id, username, password_hash, real_name,
                    organization_name, status, profile, remark)
                VALUES (:id, :username, '{bcrypt}', '社区管理员', '测试社区', 'ACTIVE',
                    CAST(:profile AS jsonb), 'BUSINESS_ACCESS_TEST')
                ON CONFLICT (username) WHERE deleted_at IS NULL
                DO UPDATE SET profile=EXCLUDED.profile, status='ACTIVE',
                    updated_at=CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("username", username)
                .addValue("profile", "{\"authorizedCommunityIds\":[\""
                        + communityId + "\"]}"));
    }
}
