package org.urbansafe.priority.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.repository.AiInferenceRepository;
import org.urbansafe.priority.ai.vision.VisionAnalysisAuditRepository;
import org.urbansafe.priority.ai.vision.VisionAnalysisOrchestrator;
import org.urbansafe.priority.ai.vision.VisionAnalysisOutcome;
import org.urbansafe.priority.asset.service.Phase2AssetService;

class AiAccuracyExecutionTaskExecutorTest {

    @Test
    void enqueuesIdempotentIntelligentAnalysisAfterSuccessfulRealVision() {
        Fixture fixture = new Fixture("SUCCEEDED");
        fixture.executor.execute(fixture.task);

        ArgumentCaptor<AiExecutionCommand> captor = ArgumentCaptor.forClass(AiExecutionCommand.class);
        verify(fixture.executionRepository).enqueue(captor.capture());
        AiExecutionCommand command = captor.getValue();
        assertThat(command.workflowCode()).isEqualTo(AiIntelligentAnalysisExecutionTaskExecutor.WORKFLOW_CODE);
        assertThat(command.idempotencyKey()).startsWith("intelligent-analysis:source-inference:");
        assertThat(command.inputs()).containsEntry("businessType", "AI_INFERENCE");
        assertThat(command.inputs()).containsEntry("businessId", fixture.buildingId.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) command.inputs().get("context");
        assertThat(context)
                .containsEntry("assetId", fixture.assetId.toString())
                .containsEntry("buildingId", fixture.buildingId.toString())
                .containsKey("sourceInferenceId");
    }

    @Test
    void doesNotEnqueueIntelligentAnalysisWhenVisionIsRejected() {
        Fixture fixture = new Fixture("REJECTED");
        fixture.executor.execute(fixture.task);
        verify(fixture.executionRepository, never()).enqueue(any());
    }

    private static final class Fixture {
        final UUID assetId = UUID.randomUUID();
        final UUID buildingId = UUID.randomUUID();
        final UUID userId = UUID.randomUUID();
        final AiExecutionTaskRepository executionRepository = mock(AiExecutionTaskRepository.class);
        final AiInferenceRepository inferenceRepository = mock(AiInferenceRepository.class);
        final Phase2AssetService assetService = mock(Phase2AssetService.class);
        final VisionAnalysisOrchestrator orchestrator = mock(VisionAnalysisOrchestrator.class);
        final VisionAnalysisAuditRepository auditRepository = mock(VisionAnalysisAuditRepository.class);
        final AiExecutionProperties properties = new AiExecutionProperties();
        final AiAccuracyExecutionTaskExecutor executor;
        final AiExecutionTask task;

        Fixture(String responseStatus) {
            UUID modelRegistryId = UUID.randomUUID();
            when(inferenceRepository.findModelByCode("AI-VISION-LOCAL-001")).thenReturn(Optional.of(Map.of(
                    "id", modelRegistryId,
                    "mode", "REAL",
                    "status", "APPROVED")));
            when(inferenceRepository.resolveAssetTraceability(assetId)).thenReturn(Optional.of(Map.of(
                    "inspectionTaskId", UUID.randomUUID(),
                    "inspectionRecordId", UUID.randomUUID(),
                    "buildingId", buildingId,
                    "communityId", UUID.randomUUID())));
            when(assetService.get(assetId)).thenReturn(Map.of(
                    "originalFilename", "inspection.jpg",
                    "contentType", "image/jpeg"));
            when(assetService.content(assetId)).thenReturn(new byte[] {1, 2, 3});
            when(inferenceRepository.markRunning(any())).thenReturn(1);
            AiInferenceResponse response = new AiInferenceResponse(
                    "req", responseStatus, "REAL",
                    new AiInferenceResponse.ModelBrief("AI-VISION-LOCAL-001", "local", "1"),
                    new AiInferenceResponse.ImageInfo(100, 100, "OK", "APPLICABLE"),
                    List.of(), new AiInferenceResponse.Summary(0, Map.of()), 20L, List.of());
            when(orchestrator.analyze(any())).thenReturn(new VisionAnalysisOutcome(
                    response, "FAST_API", "FAST_API", "LOCAL_ONLY", false, null, null, List.of()));
            when(executionRepository.enqueue(any())).thenReturn(UUID.randomUUID());

            executor = new AiAccuracyExecutionTaskExecutor(
                    executionRepository, inferenceRepository, assetService, orchestrator,
                    auditRepository, properties);
            task = new AiExecutionTask(
                    UUID.randomUUID(), assetId, "VISION", "REAL", "AI-VISION-LOCAL-001",
                    "FAST_API", "VISION_INFERENCE", null, "vision-key", userId,
                    Map.of("triggerType", "MANUAL_SINGLE"), "RUNNING", 1, 3,
                    OffsetDateTime.now(), "worker", OffsetDateTime.now().plusMinutes(1),
                    null, null, null, OffsetDateTime.now(), OffsetDateTime.now());
        }
    }
}
