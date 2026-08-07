package org.urbansafe.priority.community;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.community.command.CreateCommunityCommand;
import org.urbansafe.priority.community.result.CommunityDetailResult;
import org.urbansafe.priority.community.result.CommunityListResult;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.community.service.CommunityService;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/**
 * 小区接口集成测试。
 *
 * <p>业务接口在生产环境中要求认证，因此测试使用一个明确的管理员身份进入完整安全过滤器链；
 * 未认证、错误角色和 JWT 声明校验由独立的安全测试覆盖。</p>
 */
@WithMockUser(username = "community-controller-test", roles = "ADMIN")
class CommunityControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityService communityService;

    @BeforeEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createCommunityShouldReturn201() throws Exception {
        CommunityDetailResult result = buildCommunity("C001", "测试小区");
        when(communityService.create(any(CreateCommunityCommand.class))).thenReturn(result);

        String requestJson = """
                {
                    "communityCode": "C001",
                    "communityName": "测试小区",
                    "administrativeRegion": "测试区",
                    "address": "测试地址",
                    "householdCount": 100,
                    "residentCount": 300
                }
                """;

        mockMvc.perform(post("/api/v1/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.communityCode").value("C001"))
                .andExpect(jsonPath("$.data.communityName").value("测试小区"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void createCommunityWithDuplicateCodeShouldReturn409() throws Exception {
        when(communityService.create(any(CreateCommunityCommand.class)))
                .thenThrow(new ResourceConflictException("COMMUNITY_CODE_CONFLICT", "小区编码已存在"));

        String requestJson = """
                {
                    "communityCode": "C001",
                    "communityName": "重复小区"
                }
                """;

        mockMvc.perform(post("/api/v1/communities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_CODE_CONFLICT"));
    }

    @Test
    void getCommunityShouldReturn200() throws Exception {
        UUID communityId = UUID.randomUUID();
        CommunityDetailResult result = buildCommunity(communityId, "C001", "查询小区");
        when(communityService.get(communityId)).thenReturn(result);

        mockMvc.perform(get("/api/v1/communities/" + communityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.communityCode").value("C001"));
    }

    @Test
    void getNonexistentCommunityShouldReturn404() throws Exception {
        UUID communityId = UUID.randomUUID();
        when(communityService.get(communityId))
                .thenThrow(new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "小区不存在"));

        mockMvc.perform(get("/api/v1/communities/" + communityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void listCommunitiesShouldReturn200() throws Exception {
        CommunityDetailResult entity = buildCommunity("C001", "列表小区");
        CommunityListResult listRow = new CommunityListResult(
                entity.id(), entity.communityCode(), entity.communityName(),
                entity.administrativeRegion(), entity.address(), entity.buildingCount(),
                entity.householdCount(), entity.residentCount(), entity.status(),
                entity.createdAt(), entity.updatedAt());
        PageResult<CommunityListResult> page = new PageResult<>(List.of(listRow), 1, 20, 1, 1);
        when(communityService.page(any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/communities")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].communityCode").value("C001"));
    }

    @Test
    void deleteCommunityShouldReturn200() throws Exception {
        UUID communityId = UUID.randomUUID();
        doNothing().when(communityService).delete(communityId);

        mockMvc.perform(delete("/api/v1/communities/" + communityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resourceType").value("Community"));
    }

    @Test
    void deleteNonexistentCommunityShouldReturn404() throws Exception {
        UUID communityId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(
                new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "小区不存在"))
                .when(communityService).delete(communityId);

        mockMvc.perform(delete("/api/v1/communities/" + communityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NOT_FOUND"));
    }

    private CommunityDetailResult buildCommunity(String code, String name) {
        return buildCommunity(UUID.randomUUID(), code, name);
    }

    /** 创建 Controller 测试使用的业务结果，不向测试接口暴露持久化实体。 */
    private CommunityDetailResult buildCommunity(UUID id, String code, String name) {
        OffsetDateTime now = OffsetDateTime.now();
        return new CommunityDetailResult(
                id, code, name, "测试区", "测试地址", null,
                0, 100, 300, null, "ACTIVE", null, null,
                now, now, 0L);
    }
}
