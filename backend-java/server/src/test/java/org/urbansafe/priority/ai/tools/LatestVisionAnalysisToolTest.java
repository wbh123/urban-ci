package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.common.security.BusinessAccessService;

class LatestVisionAnalysisToolTest {

    @AfterEach
    void clearTrace() {
        AiAgentTrace.end();
    }

    @Test
    void readsLatestRealVisionFromCurrentContentPageShape() {
        UUID buildingId = UUID.randomUUID();
        UUID inferenceId = UUID.randomUUID();
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        BusinessAccessService accessService = mock(BusinessAccessService.class);
        when(inferenceService.list(any(), eq(0), eq(1))).thenReturn(Map.of(
                "content", List.of(Map.of(
                        "inferenceId", inferenceId,
                        "status", "SUCCEEDED",
                        "reviewStatus", "CONFIRMED",
                        "modelId", "AI-VISION-LOCAL-001",
                        "detectionCount", 3))));
        when(inferenceService.getDetail(inferenceId)).thenReturn(Map.of(
                "inferenceId", inferenceId,
                "buildingId", buildingId,
                "status", "SUCCEEDED",
                "mode", "REAL",
                "reviewStatus", "CONFIRMED",
                "modelId", "AI-VISION-LOCAL-001",
                "detectionCount", 3,
                "detections", List.of(Map.of(), Map.of(), Map.of())));

        LatestVisionAnalysisTool.LatestVisionResult result =
                new LatestVisionAnalysisTool(inferenceService, accessService)
                        .latest(buildingId.toString());

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.modelId()).isEqualTo("AI-VISION-LOCAL-001");
        assertThat(result.detectionCount()).isEqualTo(3);
        assertThat(result.inferenceId()).isEqualTo(inferenceId.toString());
    }

    @Test
    void boundSourceInferenceBypassesBuildingLatestSeedResult() {
        UUID buildingId = UUID.randomUUID();
        UUID sourceInferenceId = UUID.randomUUID();
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        BusinessAccessService accessService = mock(BusinessAccessService.class);
        AiAgentExecution execution = new AiAgentExecution(
                UUID.randomUUID(), "AI_INFERENCE", buildingId, "自动研判", UUID.randomUUID(), "tester");
        AiAgentTrace.begin(execution, Map.of("sourceInferenceId", sourceInferenceId.toString()));
        when(inferenceService.getDetail(sourceInferenceId)).thenReturn(Map.of(
                "inferenceId", sourceInferenceId,
                "buildingId", buildingId,
                "assetId", UUID.randomUUID(),
                "status", "SUCCEEDED",
                "mode", "REAL",
                "reviewStatus", "UNREVIEWED",
                "modelId", "AI-VISION-LOCAL-001",
                "detectionCount", 2,
                "detections", List.of(Map.of(), Map.of())));

        LatestVisionAnalysisTool.LatestVisionResult result =
                new LatestVisionAnalysisTool(inferenceService, accessService)
                        .latest(buildingId.toString());

        assertThat(result.inferenceId()).isEqualTo(sourceInferenceId.toString());
        assertThat(result.detectionCount()).isEqualTo(2);
        verify(inferenceService, never()).list(any(), eq(0), eq(1));
    }

    @Test
    void filtersOnlySucceededRealVision() {
        UUID buildingId = UUID.randomUUID();
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        BusinessAccessService accessService = mock(BusinessAccessService.class);
        when(inferenceService.list(any(), eq(0), eq(1))).thenReturn(Map.of("content", List.of()));

        new LatestVisionAnalysisTool(inferenceService, accessService).latest(buildingId.toString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(inferenceService).list(captor.capture(), eq(0), eq(1));
        assertThat(captor.getValue())
                .containsEntry("mode", "REAL")
                .containsEntry("status", "SUCCEEDED");
    }
}
