package org.urbansafe.priority.ai.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;
import org.urbansafe.priority.ai.orchestration.SpringAiLocalVisionOrchestrator;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.provider.DifyWorkflowProvider;

class VisionAnalysisOrchestratorTest {

    @Test
    void shouldUseLocalAccuracyWhenDifyWorkflowIsDisabled() {
        AiAutomationSettingsService settings = mock(AiAutomationSettingsService.class);
        DifyWorkflowProvider dify = mock(DifyWorkflowProvider.class);
        SpringAiLocalVisionOrchestrator local = localOrchestrator();
        when(settings.intelligentWorkflowEnabled()).thenReturn(false);

        VisionAnalysisOutcome result = new VisionAnalysisOrchestrator(settings, dify, local)
                .analyze(request());

        assertThat(result.response().detections()).hasSize(1);
        assertThat(result.preferredProvider()).isEqualTo("FAST_API");
        assertThat(result.actualProvider()).isEqualTo("FAST_API");
        assertThat(result.orchestrationMode()).isEqualTo("SPRING_AI_LOCAL");
        assertThat(result.fallback()).isFalse();
        verify(dify, never()).execute(any());
        verify(local).analyze(any());
    }

    @Test
    void shouldPreferDifyButKeepLocalAccuracyAsProfessionalVisualResult() {
        AiAutomationSettingsService settings = mock(AiAutomationSettingsService.class);
        DifyWorkflowProvider dify = mock(DifyWorkflowProvider.class);
        SpringAiLocalVisionOrchestrator local = localOrchestrator();
        when(settings.intelligentWorkflowEnabled()).thenReturn(true);
        when(dify.enabled()).thenReturn(true);
        when(dify.configured()).thenReturn(true);
        when(dify.execute(any())).thenReturn(difyResult());

        VisionAnalysisOutcome result = new VisionAnalysisOrchestrator(settings, dify, local)
                .analyze(request());

        assertThat(result.response().detections()).hasSize(1);
        assertThat(result.preferredProvider()).isEqualTo("DIFY");
        assertThat(result.actualProvider()).isEqualTo("DIFY");
        assertThat(result.orchestrationMode()).isEqualTo("DIFY_PREFERRED");
        assertThat(result.fallback()).isFalse();
        assertThat(result.difySummary()).isEqualTo("Dify 语义整理完成");
        verify(local).analyze(any());
        verify(dify).execute(any());
    }

    @Test
    void shouldFallBackToLocalAccuracyWhenDifyTimesOut() {
        AiAutomationSettingsService settings = mock(AiAutomationSettingsService.class);
        DifyWorkflowProvider dify = mock(DifyWorkflowProvider.class);
        SpringAiLocalVisionOrchestrator local = localOrchestrator();
        when(settings.intelligentWorkflowEnabled()).thenReturn(true);
        when(dify.enabled()).thenReturn(true);
        when(dify.configured()).thenReturn(true);
        when(dify.execute(any())).thenThrow(new AiProviderException(
                AiErrorCodes.AI_PROVIDER_TIMEOUT, "timeout"));

        VisionAnalysisOutcome result = new VisionAnalysisOrchestrator(settings, dify, local)
                .analyze(request());

        assertThat(result.response().detections()).hasSize(1);
        assertThat(result.preferredProvider()).isEqualTo("DIFY");
        assertThat(result.actualProvider()).isEqualTo("FAST_API");
        assertThat(result.orchestrationMode()).isEqualTo("SPRING_AI_LOCAL");
        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(AiErrorCodes.AI_PROVIDER_TIMEOUT);
        verify(local).analyze(any());
    }

    @Test
    void shouldFallBackToLocalAccuracyWhenDifyWorkflowRunFails() {
        AiAutomationSettingsService settings = mock(AiAutomationSettingsService.class);
        DifyWorkflowProvider dify = mock(DifyWorkflowProvider.class);
        SpringAiLocalVisionOrchestrator local = localOrchestrator();
        when(settings.intelligentWorkflowEnabled()).thenReturn(true);
        when(dify.enabled()).thenReturn(true);
        when(dify.configured()).thenReturn(true);
        when(dify.execute(any())).thenThrow(new AiProviderException(
                AiErrorCodes.AI_WORKFLOW_FAILED, "workflow failed"));

        VisionAnalysisOutcome result = new VisionAnalysisOrchestrator(settings, dify, local)
                .analyze(request());

        assertThat(result.actualProvider()).isEqualTo("FAST_API");
        assertThat(result.orchestrationMode()).isEqualTo("SPRING_AI_LOCAL");
        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).isEqualTo(AiErrorCodes.AI_WORKFLOW_FAILED);
    }

    @Test
    void shouldNotHideInvalidDifyBusinessResponseBehindLocalFallback() {
        AiAutomationSettingsService settings = mock(AiAutomationSettingsService.class);
        DifyWorkflowProvider dify = mock(DifyWorkflowProvider.class);
        SpringAiLocalVisionOrchestrator local = localOrchestrator();
        when(settings.intelligentWorkflowEnabled()).thenReturn(true);
        when(dify.enabled()).thenReturn(true);
        when(dify.configured()).thenReturn(true);
        when(dify.execute(any())).thenThrow(new AiProviderException(
                AiErrorCodes.AI_INVALID_RESPONSE, "invalid response"));

        VisionAnalysisOrchestrator orchestrator = new VisionAnalysisOrchestrator(settings, dify, local);

        assertThatThrownBy(() -> orchestrator.analyze(request()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("invalid response");
        verify(local).analyze(any());
    }

    private static SpringAiLocalVisionOrchestrator localOrchestrator() {
        SpringAiLocalVisionOrchestrator local = mock(SpringAiLocalVisionOrchestrator.class);
        when(local.analyze(any())).thenReturn(localResponse());
        return local;
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

    private static AiInferenceResponse localResponse() {
        AiInferenceResponse.Detection detection = new AiInferenceResponse.Detection(
                1,
                "CRACK",
                "裂缝",
                0.91,
                new AiInferenceResponse.BoundingBox(0.1, 0.2, 0.3, 0.4, "NORMALIZED_XYWH"),
                null);
        return new AiInferenceResponse(
                "REQ-1",
                "SUCCEEDED",
                "REAL",
                new AiInferenceResponse.ModelBrief("AI-VISION-LOCAL-001", "本地视觉", "1.0.0"),
                new AiInferenceResponse.ImageInfo(1000, 800, "OK", "APPLICABLE"),
                List.of(detection),
                new AiInferenceResponse.Summary(1, Map.of("CRACK", 1)),
                1000,
                List.of());
    }

    private static AiOrchestrationResult difyResult() {
        return new AiOrchestrationResult(
                "REQ-1",
                "DIFY",
                "DIFY-IMAGE-ANALYSIS-001",
                "image-analysis-v1.1.0",
                AiCapabilityType.WORKFLOW,
                "SUCCEEDED",
                "Dify 语义整理完成",
                List.of(),
                List.of(),
                List.of("建议人工复核"),
                0.8,
                List.of(),
                "dify:run-1",
                500);
    }
}
