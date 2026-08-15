package org.urbansafe.priority.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.provider.FastApiAiInferenceProvider;
import org.urbansafe.priority.ai.vision.VisionAnalysisRequest;

class SpringAiLocalVisionOrchestratorTest {

    @Test
    void shouldInvokeApprovedLocalVisionWithAccuracyMetadataWithoutDeepSeek() {
        FastApiAiInferenceProvider local = mock(FastApiAiInferenceProvider.class);
        HashMap<String, Object> metadata = new HashMap<>();
        when(local.buildMetadata(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(metadata);
        when(local.infer(any(), anyMap(), anyString())).thenReturn(response());

        AiInferenceResponse result = new SpringAiLocalVisionOrchestrator(local).analyze(request());

        verify(local).requireModelReady("AI-VISION-LOCAL-001", "REAL");
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(local).infer(any(), metadataCaptor.capture(), anyString());
        assertThat(metadataCaptor.getValue()).containsEntry("inferenceProfile", "ACCURACY");
        assertThat(metadataCaptor.getValue()).containsEntry("triggerType", "MANUAL_SINGLE");
        assertThat(metadataCaptor.getValue()).containsEntry("orchestrator", "SPRING_AI_LOCAL");
        assertThat(result.status()).isEqualTo("SUCCEEDED");
    }

    private static VisionAnalysisRequest request() {
        return new VisionAnalysisRequest(
                "REQ-1",
                "AI-VISION-LOCAL-001",
                "asset-1",
                "inspection.jpg",
                "image/jpeg",
                new byte[] {1, 2, 3},
                "MANUAL_SINGLE");
    }

    private static AiInferenceResponse response() {
        return new AiInferenceResponse(
                "REQ-1",
                "SUCCEEDED",
                "REAL",
                new AiInferenceResponse.ModelBrief("AI-VISION-LOCAL-001", "本地视觉", "1.0.0"),
                new AiInferenceResponse.ImageInfo(1000, 800, "OK", "APPLICABLE"),
                List.of(),
                new AiInferenceResponse.Summary(0, Map.of()),
                1000,
                List.of());
    }
}
