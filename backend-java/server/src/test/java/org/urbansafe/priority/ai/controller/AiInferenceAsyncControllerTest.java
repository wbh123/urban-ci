package org.urbansafe.priority.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.ai.execution.AiExecutionTaskQueryService;
import org.urbansafe.priority.ai.execution.AiExecutionTaskService;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.ai.service.AiRichDetectionDetailService;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

@WithMockUser(username = "accuracy-controller", roles = "ADMIN")
class AiInferenceAsyncControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @MockitoBean AiInferenceService inferenceService;
    @MockitoBean AiExecutionTaskService executionTaskService;
    @MockitoBean AiExecutionTaskQueryService executionTaskQueryService;
    @MockitoBean AiRichDetectionDetailService richDetectionDetailService;

    @Test
    void accuracySubmissionReturns202WithoutWaitingForInference() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(executionTaskService.enqueue(any())).thenReturn(taskId);

        mockMvc.perform(post("/api/v1/ai-inferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetId":"44444444-4444-4444-4444-444444444444",
                                 "mode":"REAL","modelId":"AI-VISION-LOCAL-001",
                                 "providerCode":"FAST_API","capabilityType":"VISION_INFERENCE",
                                 "inferenceProfile":"ACCURACY"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void executionStatusCanBePolledWithShortGet() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(executionTaskQueryService.get(taskId)).thenReturn(Map.of(
                "taskId", taskId, "status", "RUNNING"));

        mockMvc.perform(get("/api/v1/ai-inference-executions/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }
}
