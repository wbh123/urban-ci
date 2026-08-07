package org.urbansafe.priority.building;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.building.result.BuildingListResult;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.common.security.CommunityAccessScope;
import org.urbansafe.priority.common.security.ScopedArchiveQueryService;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 楼栋控制器的方法级角色与辖区范围测试。 */
class BuildingControllerSecurityIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BuildingService buildingService;

    @MockitoBean
    private BusinessAccessService accessService;

    @MockitoBean
    private ScopedArchiveQueryService scopedQueryService;

    @Test
    @WithMockUser(username = "field", roles = "PROPERTY_INSPECTOR")
    void fieldRoleCannotUseGenericBuildingDirectory() throws Exception {
        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(username = "scope-manager", roles = "COMMUNITY_MANAGER")
    void restrictedBuildingListShouldUseDatabaseScope() throws Exception {
        UUID communityId = UUID.randomUUID();
        CommunityAccessScope scope = CommunityAccessScope.restricted(Set.of(communityId));
        when(accessService.currentCommunityScope()).thenReturn(scope);
        when(scopedQueryService.listBuildings(
                any(), any(), any(ApiPageRequest.class), any(), eq(scope)))
                .thenReturn(new PageResult<BuildingListResult>(java.util.List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/buildings").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        verify(scopedQueryService).listBuildings(
                any(), any(), any(ApiPageRequest.class), any(), eq(scope));
    }

    @Test
    @WithMockUser(username = "scope-manager", roles = "COMMUNITY_MANAGER")
    void createBuildingShouldCheckTargetCommunityBeforeService() throws Exception {
        UUID communityId = UUID.randomUUID();
        doThrow(new AccessDeniedException("BUSINESS_ARCHIVE_ACCESS_DENIED"))
                .when(accessService).assertCanCreateBuilding(communityId);

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "communityId": "%s",
                                  "buildingCode": "B100",
                                  "buildingName": "越权楼栋",
                                  "householdCount": 10,
                                  "residentCount": 30
                                }
                                """.formatted(communityId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(username = "scope-manager", roles = "COMMUNITY_MANAGER")
    void crossCommunityBuildingReadShouldReturn403() throws Exception {
        UUID buildingId = UUID.randomUUID();
        doThrow(new AccessDeniedException("BUSINESS_ARCHIVE_ACCESS_DENIED"))
                .when(accessService).assertCanReadBuilding(buildingId);

        mockMvc.perform(get("/api/v1/buildings/" + buildingId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }
}
