package org.urbansafe.priority.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.ai.converter.AiInferenceConverter;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 人工智能推理接口集成测试，验证控制器路由、响应外壳、状态码和关键角色权限。 */
@WithMockUser(username = "ai-controller-test", roles = "ADMIN")
class AiInferenceControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiInferenceService aiInferenceService;

    @BeforeEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createInferenceShouldReturn201() throws Exception {
        UUID inferenceId = UUID.randomUUID();
        when(aiInferenceService.create(any())).thenReturn(Map.of(
                "inferenceId", inferenceId, "status", "SUCCEEDED", "mode", "MOCK",
                "disclaimer", AiInferenceConverter.DISCLAIMER));

        String requestJson = """
                {
                    "assetId": "44444444-4444-4444-4444-444444444444",
                    "mode": "MOCK",
                    "modelId": "AI-DEFECT-MOCK-001",
                    "idempotencyKey": "key-1"
                }
                """;
        mockMvc.perform(post("/api/v1/ai-inferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inferenceId").value(inferenceId.toString()))
                .andExpect(jsonPath("$.data.mode").value("MOCK"))
                .andExpect(jsonPath("$.data.disclaimer").exists());
    }

    @Test
    void createInferenceWithUnknownAssetShouldReturn404() throws Exception {
        when(aiInferenceService.create(any()))
                .thenThrow(new ResourceNotFoundException("AI_ASSET_NOT_FOUND", "图片不存在"));

        String requestJson = """
                {
                    "assetId": "44444444-4444-4444-4444-444444444499",
                    "mode": "MOCK"
                }
                """;
        mockMvc.perform(post("/api/v1/ai-inferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AI_ASSET_NOT_FOUND"));
    }

    @Test
    void getInferenceShouldReturn200() throws Exception {
        UUID inferenceId = UUID.randomUUID();
        when(aiInferenceService.getDetail(inferenceId)).thenReturn(Map.of(
                "inferenceId", inferenceId, "status", "SUCCEEDED"));

        mockMvc.perform(get("/api/v1/ai-inferences/" + inferenceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inferenceId").value(inferenceId.toString()));
    }

    @Test
    void listInferencesShouldReturnPage() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", List.of(Map.of("inferenceId", UUID.randomUUID(), "status", "SUCCEEDED")));
        data.put("page", Map.of("page", 0, "size", 20, "totalElements", 1, "totalPages", 1));
        when(aiInferenceService.list(any(), anyInt(), anyInt())).thenReturn(data);

        mockMvc.perform(get("/api/v1/ai-inferences").param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    void retryInferenceShouldReturn201() throws Exception {
        UUID inferenceId = UUID.randomUUID();
        when(aiInferenceService.retry(any())).thenReturn(Map.of(
                "inferenceId", inferenceId, "status", "SUCCEEDED"));

        mockMvc.perform(post("/api/v1/ai-inferences/" + inferenceId + "/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"AI-DEFECT-MOCK-001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void reviewInferenceShouldReturn200() throws Exception {
        UUID inferenceId = UUID.randomUUID();
        when(aiInferenceService.review(any())).thenReturn(Map.of(
                "inferenceId", inferenceId, "reviewStatus", "CONFIRMED"));

        mockMvc.perform(post("/api/v1/ai-inferences/" + inferenceId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"CONFIRMED\",\"comment\":\"确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("CONFIRMED"));
    }


    @Test
    @WithMockUser(username = "professional-reviewer", roles = "PROFESSIONAL_REVIEWER")
    void reviewInferenceShouldAllowProfessionalReviewer() throws Exception {
        UUID inferenceId = UUID.randomUUID();
        when(aiInferenceService.review(any())).thenReturn(Map.of(
                "inferenceId", inferenceId, "reviewStatus", "CORRECTED"));

        mockMvc.perform(post("/api/v1/ai-inferences/" + inferenceId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewStatus":"CORRECTED","comment":"修正"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("CORRECTED"));
    }

    @Test
    @WithMockUser(username = "property-inspector", roles = "PROPERTY_INSPECTOR")
    void reviewInferenceShouldRejectPropertyInspector() throws Exception {
        UUID inferenceId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/ai-inferences/" + inferenceId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewStatus\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }
}
