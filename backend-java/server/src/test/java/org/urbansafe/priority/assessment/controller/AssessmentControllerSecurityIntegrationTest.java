package org.urbansafe.priority.assessment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.assessment.rule.RuleVersionService;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 评分接口真实安全过滤器、方法级权限和楼栋数据范围测试。 */
class AssessmentControllerSecurityIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @MockitoBean
    private AssessmentApplicationService assessmentService;

    @MockitoBean
    private RuleVersionService ruleVersionService;

    private UUID allowedCommunityId;
    private UUID otherCommunityId;
    private UUID allowedBuildingId;
    private UUID otherBuildingId;

    @BeforeEach
    void setUp() {
        RequestContext.clear();
        allowedCommunityId = UUID.randomUUID();
        otherCommunityId = UUID.randomUUID();
        allowedBuildingId = UUID.randomUUID();
        otherBuildingId = UUID.randomUUID();
        String token = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        insertCommunity(allowedCommunityId, "SEC-A-" + token);
        insertCommunity(otherCommunityId, "SEC-B-" + token);
        insertBuilding(allowedBuildingId, allowedCommunityId, "SEC-A-B1-" + token);
        insertBuilding(otherBuildingId, otherCommunityId, "SEC-B-B1-" + token);
        insertCommunityManager("cm-one", allowedCommunityId);

        when(assessmentService.summary(any())).thenReturn(Map.of(
                "buildingId", allowedBuildingId,
                "buildingCode", "SEC-A-B1",
                "buildingName", "权限测试楼栋",
                "communityId", allowedCommunityId,
                "communityName", "权限测试小区",
                "freshness", "STALE",
                "priority", Map.of("priorityScore", 88.0, "priorityLevel", "P1", "rankingScopeKey", "ALL"),
                "disclaimer", "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。"));
        when(assessmentService.current(any())).thenReturn(Map.of(
                "buildingId", allowedBuildingId,
                "freshness", "NO_RESULT",
                "disclaimer", "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。"));
        when(assessmentService.calculate(any(), anyBoolean(), any(), any(), any())).thenReturn(Map.of(
                "buildingId", allowedBuildingId,
                "reused", Boolean.TRUE,
                "excludedEvidenceCount", 0,
                "renewalPriorities", java.util.List.of(),
                "warnings", java.util.List.of(),
                "disclaimer", "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。"));
        when(assessmentService.ranking(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Map.of(
                "scopeKey", "ALL",
                "content", java.util.List.of(rankingRow()),
                "page", Map.of("page", 0, "size", 20, "totalElements", 1, "totalPages", 1),
                "disclaimer", "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。"));
    }

    @Test
    void summaryShouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/assessments/buildings/" + allowedBuildingId + "/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    @WithMockUser(username = "field", roles = "PROPERTY_INSPECTOR")
    void propertyInspectorCanReadSummaryButCannotReadFullAssessment() throws Exception {
        mockMvc.perform(get("/api/v1/assessments/buildings/" + allowedBuildingId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.freshness").value("STALE"))
                .andExpect(jsonPath("$.data.priority").doesNotExist());

        mockMvc.perform(get("/api/v1/assessments/buildings/" + allowedBuildingId + "/current"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    @WithMockUser(username = "dispose", roles = "DISPOSAL_OPERATOR")
    void disposalOperatorCanReadSummaryWithoutPriority() throws Exception {
        mockMvc.perform(get("/api/v1/assessments/buildings/" + allowedBuildingId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.priority").doesNotExist());
    }

    @Test
    @WithMockUser(username = "gov", roles = "GOVERNMENT_MANAGER")
    void governmentManagerCanReadPriorityThroughRanking() throws Exception {
        mockMvc.perform(get("/api/v1/renewal-priorities").param("scopeType", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].priorityScore").value(88.0));
    }

    @Test
    @WithMockUser(username = "cm-one", roles = "COMMUNITY_MANAGER")
    void communityManagerCanReadAndCalculateOnlyOwnCommunityBuilding() throws Exception {
        mockMvc.perform(get("/api/v1/assessments/buildings/" + allowedBuildingId + "/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/assessments/buildings/" + allowedBuildingId + "/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/assessments/buildings/" + otherBuildingId + "/current"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));

        mockMvc.perform(post("/api/v1/assessments/buildings/" + otherBuildingId + "/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }


    private Map<String, Object> rankingRow() {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("ranking", 1);
        row.put("buildingId", allowedBuildingId);
        row.put("buildingCode", "SEC-A-B1");
        row.put("buildingName", "权限测试楼栋");
        row.put("communityId", allowedCommunityId);
        row.put("communityName", "权限测试小区");
        row.put("priorityScore", 88.0);
        row.put("priorityLevel", "P1");
        row.put("riskScore", 76.0);
        row.put("riskLevel", "VERY_HIGH");
        row.put("confidenceScore", 82.0);
        row.put("residentCount", 30);
        row.put("mainReasons", java.util.List.of("风险高"));
        row.put("needManualReview", true);
        row.put("needProfessionalInspection", true);
        row.put("rankingScopeKey", "ALL");
        row.put("status", "CURRENT");
        row.put("generatedAt", "2026-07-25T00:00:00Z");
        row.put("disclaimer", "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。");
        return row;
    }

    private void insertCommunity(UUID communityId, String code) {
        jdbc.update("""
                INSERT INTO core.community(id, community_code, community_name)
                VALUES (:id, :code, :name)
                """, Map.of("id", communityId, "code", code, "name", code + "小区"));
    }

    private void insertBuilding(UUID buildingId, UUID communityId, String code) {
        jdbc.update("""
                INSERT INTO core.building(id, community_id, building_code, building_name)
                VALUES (:id, :communityId, :code, :name)
                """, Map.of("id", buildingId, "communityId", communityId, "code", code, "name", code + "楼栋"));
    }

    private void insertCommunityManager(String username, UUID communityId) {
        jdbc.update("""
                INSERT INTO core.user_account(id, username, password_hash, real_name,
                    organization_name, status, profile, remark)
                VALUES (:id, :username, '{bcrypt}', '社区经理', '权限测试小区', 'ACTIVE',
                    CAST(:profile AS jsonb), 'ASSESSMENT_SECURITY_TEST')
                ON CONFLICT (username) WHERE deleted_at IS NULL
                DO UPDATE SET profile=EXCLUDED.profile, updated_at=CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("username", username)
                .addValue("profile", "{\"authorizedCommunityIds\":[\"" + communityId + "\"]}"));
    }
}
