package org.urbansafe.priority.building;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.building.command.CreateBuildingCommand;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/**
 * 楼栋接口集成测试。
 *
 * <p>统一注入已认证管理员身份，使每个请求都经过真实 Spring Security 过滤器链，
 * 同时把测试关注点保持在楼栋接口状态码和统一响应契约。</p>
 */
@WithMockUser(username = "building-controller-test", roles = "ADMIN")
class BuildingControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BuildingService buildingService;

    @BeforeEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createBuildingShouldReturn201() throws Exception {
        BuildingDetailResult response = buildBuildingResponse("B001", "测试楼栋");
        when(buildingService.createBuilding(any(CreateBuildingCommand.class))).thenReturn(response);

        String requestJson = """
                {
                    "communityId": "00000000-0000-0000-0000-000000000001",
                    "buildingCode": "B001",
                    "buildingName": "测试楼栋",
                    "householdCount": 24,
                    "residentCount": 72
                }
                """;

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.buildingCode").value("B001"))
                .andExpect(jsonPath("$.data.buildingName").value("测试楼栋"));
    }

    @Test
    void createBuildingWithNonexistentCommunityShouldReturn404() throws Exception {
        when(buildingService.createBuilding(any(CreateBuildingCommand.class)))
                .thenThrow(new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "所属小区不存在"));

        String requestJson = """
                {
                    "communityId": "00000000-0000-0000-0000-000000000099",
                    "buildingCode": "B001",
                    "householdCount": 10,
                    "residentCount": 30
                }
                """;

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void createBuildingWithDuplicateCodeShouldReturn409() throws Exception {
        when(buildingService.createBuilding(any(CreateBuildingCommand.class)))
                .thenThrow(new ResourceConflictException("BUILDING_CODE_CONFLICT", "同小区内楼栋编码已存在"));

        String requestJson = """
                {
                    "communityId": "00000000-0000-0000-0000-000000000001",
                    "buildingCode": "B001",
                    "householdCount": 10,
                    "residentCount": 30
                }
                """;

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUILDING_CODE_CONFLICT"));
    }

    @Test
    void getBuildingShouldReturn200() throws Exception {
        UUID buildingId = UUID.randomUUID();
        BuildingDetailResult response = buildBuildingResponse(buildingId, "B001", "查询楼栋");
        when(buildingService.getBuilding(buildingId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/buildings/" + buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.buildingCode").value("B001"));
    }

    @Test
    void getNonexistentBuildingShouldReturn404() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(buildingService.getBuilding(buildingId))
                .thenThrow(new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在"));

        mockMvc.perform(get("/api/v1/buildings/" + buildingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BUILDING_NOT_FOUND"));
    }

    /** 创建不依赖 OpenAPI DTO 的楼栋详情 Service 结果。 */
    private BuildingDetailResult buildBuildingResponse(String code, String name) {
        return buildBuildingResponse(UUID.randomUUID(), code, name);
    }

    /** 创建指定标识的楼栋详情 Service 结果。 */
    private BuildingDetailResult buildBuildingResponse(UUID id, String code, String name) {
        OffsetDateTime now = OffsetDateTime.now();
        return new BuildingDetailResult(id, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                code, name, null, null, null, null, null, 24, 72, null, null,
                null, null, null, null, "ACTIVE", null, null, now, now, 0L);
    }
}
