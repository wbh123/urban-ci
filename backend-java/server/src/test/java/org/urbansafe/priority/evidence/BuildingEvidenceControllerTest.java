package org.urbansafe.priority.evidence;

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
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.evidence.service.BuildingEvidenceService;
import org.urbansafe.priority.evidence.command.CreateEvidenceCommand;
import org.urbansafe.priority.evidence.result.EvidenceDetailResult;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/**
 * 楼栋证据接口集成测试。
 *
 * <p>使用显式测试身份访问受保护接口，避免关闭过滤器造成测试环境与生产授权行为分叉。</p>
 */
@WithMockUser(username = "evidence-controller-test", roles = "ADMIN")
class BuildingEvidenceControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BuildingEvidenceService buildingEvidenceService;

    @BeforeEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createEvidenceShouldReturn201() throws Exception {
        UUID buildingId = UUID.randomUUID();
        EvidenceDetailResult response = buildEvidenceResponse(buildingId);
        when(buildingEvidenceService.createBuildingEvidence(eq(buildingId), any(CreateEvidenceCommand.class)))
                .thenReturn(response);

        String requestJson = """
                {
                    "evidenceType": "MAINTENANCE_RECORD",
                    "title": "外墙裂缝维修记录",
                    "description": "2024年3月发现外墙裂缝",
                    "source": "物业报修系统",
                    "reliabilityLevel": "PROFESSIONAL_CONFIRMED"
                }
                """;

        mockMvc.perform(post("/api/v1/buildings/" + buildingId + "/evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceType").value("MAINTENANCE_RECORD"))
                .andExpect(jsonPath("$.data.title").value("外墙裂缝维修记录"));
    }

    @Test
    void createEvidenceForNonexistentBuildingShouldReturn404() throws Exception {
        UUID buildingId = UUID.randomUUID();
        when(buildingEvidenceService.createBuildingEvidence(eq(buildingId), any(CreateEvidenceCommand.class)))
                .thenThrow(new ResourceNotFoundException("BUILDING_NOT_FOUND", "父楼栋不存在"));

        String requestJson = """
                {
                    "evidenceType": "MAINTENANCE_RECORD",
                    "title": "测试证据",
                    "source": "测试",
                    "reliabilityLevel": "UNVERIFIED"
                }
                """;

        mockMvc.perform(post("/api/v1/buildings/" + buildingId + "/evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("BUILDING_NOT_FOUND"));
    }

    /** 验证不受 OpenAPI 契约支持的枚举值被统一映射为 400，而不是泄漏为 500。 */
    @Test
    void createEvidenceWithUnsupportedReliabilityShouldReturn400() throws Exception {
        UUID buildingId = UUID.randomUUID();
        String requestJson = """
                {
                    "evidenceType": "MAINTENANCE_RECORD",
                    "title": "非法可靠性测试",
                    "reliabilityLevel": "VERIFIED"
                }
                """;

        mockMvc.perform(post("/api/v1/buildings/" + buildingId + "/evidence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REQUEST_BODY_INVALID"));
    }

    @Test
    void getEvidenceShouldReturn200() throws Exception {
        UUID evidenceId = UUID.randomUUID();
        EvidenceDetailResult response = buildEvidenceResponse(evidenceId, UUID.randomUUID());
        when(buildingEvidenceService.getBuildingEvidence(evidenceId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/building-evidence/" + evidenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceType").value("MAINTENANCE_RECORD"));
    }

    @Test
    void getNonexistentEvidenceShouldReturn404() throws Exception {
        UUID evidenceId = UUID.randomUUID();
        when(buildingEvidenceService.getBuildingEvidence(evidenceId))
                .thenThrow(new ResourceNotFoundException("EVIDENCE_NOT_FOUND", "证据不存在"));

        mockMvc.perform(get("/api/v1/building-evidence/" + evidenceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVIDENCE_NOT_FOUND"));
    }

    /** 创建不依赖 OpenAPI DTO 的楼栋证据详情 Service 结果。 */
    private EvidenceDetailResult buildEvidenceResponse(UUID buildingId) {
        return buildEvidenceResponse(UUID.randomUUID(), buildingId);
    }

    /** 创建指定标识的楼栋证据详情 Service 结果。 */
    private EvidenceDetailResult buildEvidenceResponse(UUID evidenceId, UUID buildingId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new EvidenceDetailResult(evidenceId, buildingId, "MAINTENANCE_RECORD",
                "外墙裂缝维修记录", "2024年3月发现外墙裂缝并进行维修", null,
                "物业报修系统", "PROFESSIONAL_CONFIRMED", null, null, now, now, 0L);
    }
}
