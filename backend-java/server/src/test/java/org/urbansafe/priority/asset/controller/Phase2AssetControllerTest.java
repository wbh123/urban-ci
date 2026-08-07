package org.urbansafe.priority.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.urbansafe.priority.ai.automation.AiUploadAutomationResult;
import org.urbansafe.priority.ai.automation.AiUploadAutomationService;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 图片资产接口兼容性测试。 */
@WithMockUser(username = "asset-controller-test", roles = "ADMIN")
class Phase2AssetControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Phase2AssetService assetService;

    @MockitoBean
    private AiUploadAutomationService automationService;

    @Test
    void listImagesAliasShouldReturnBoundAssets() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(assetService.list("INSPECTION_TASK", businessId)).thenReturn(List.of(Map.of(
                "assetId", assetId,
                "originalFilename", "inspection.jpg",
                "contentType", "image/jpeg",
                "storageProvider", "LOCAL")));

        mockMvc.perform(get("/api/v1/assets/images")
                        .param("businessType", "INSPECTION_TASK")
                        .param("businessId", businessId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.data[0].originalFilename").value("inspection.jpg"));
    }

    @Test
    void uploadShouldReturnAutomaticInferenceOutcomeWithoutChangingUploadSuccess() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID executionTaskId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "inspection.jpg", "image/jpeg", "image".getBytes());
        when(assetService.upload(
                any(MultipartFile.class),
                eq("INSPECTION_TASK"),
                eq(businessId),
                eq("INSPECTION_PHOTO")))
                .thenReturn(Map.of(
                        "assetId", assetId,
                        "originalFilename", "inspection.jpg",
                        "contentType", "image/jpeg",
                        "storageProvider", "LOCAL"));
        when(automationService.triggerIfEnabled(assetId, "INSPECTION_TASK", null))
                .thenReturn(new AiUploadAutomationResult(
                        true,
                        true,
                        true,
                        "AI-DIFY-WORKFLOW-001",
                        executionTaskId,
                        null,
                        "PENDING",
                        "图片上传完成，自动识别任务已进入后台队列"));

        mockMvc.perform(multipart("/api/v1/assets/images")
                        .file(file)
                        .param("businessType", "INSPECTION_TASK")
                        .param("businessId", businessId.toString())
                        .param("bindingRole", "INSPECTION_PHOTO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assetId").value(assetId.toString()))
                .andExpect(jsonPath("$.data.autoInference.enabled").value(true))
                .andExpect(jsonPath("$.data.autoInference.triggered").value(true))
                .andExpect(jsonPath("$.data.autoInference.queued").value(true))
                .andExpect(jsonPath("$.data.autoInference.executionTaskId").value(executionTaskId.toString()))
                .andExpect(jsonPath("$.data.autoInference.inferenceId").doesNotExist())
                .andExpect(jsonPath("$.data.autoInference.status").value("PENDING"));
    }
}
