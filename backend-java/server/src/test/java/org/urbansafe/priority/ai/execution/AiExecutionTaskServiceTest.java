package org.urbansafe.priority.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.command.CreateInferenceCommand;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.service.AiInferenceService;

class AiExecutionTaskServiceTest {

    @Test
    void enqueueShouldPersistStableCommandAndReturnTaskId() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        UUID expected = UUID.randomUUID();
        when(repository.enqueue(any())).thenReturn(expected);
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionCommand command = command();

        UUID actual = service.enqueue(command);

        assertThat(actual).isEqualTo(expected);
        verify(repository).enqueue(command);
    }

    @Test
    void executeClaimedShouldCreateInferenceAndMarkSucceeded() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(1, 3);
        UUID inferenceId = UUID.randomUUID();
        when(inferenceService.create(any())).thenReturn(Map.of(
                "inferenceId", inferenceId,
                "status", "SUCCEEDED"));

        service.executeClaimed(task);

        ArgumentCaptor<CreateInferenceCommand> commandCaptor =
                ArgumentCaptor.forClass(CreateInferenceCommand.class);
        verify(inferenceService).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().assetId()).isEqualTo(task.assetId());
        assertThat(commandCaptor.getValue().modelId()).isEqualTo(task.modelId());
        verify(repository).markSucceeded(task.id(), inferenceId);
    }

    @Test
    void executeClaimedShouldScheduleRetryForProviderTimeout() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        properties.setRetryBaseDelaySeconds(5);
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(1, 3);
        when(inferenceService.create(any())).thenReturn(Map.of(
                "status", "FAILED",
                "errorCode", AiErrorCodes.AI_PROVIDER_TIMEOUT,
                "errorMessage", "timeout"));

        service.executeClaimed(task);

        verify(repository).markRetry(any(UUID.class), any(OffsetDateTime.class),
                eq(AiErrorCodes.AI_PROVIDER_TIMEOUT), eq("timeout"));
        verify(repository, never()).markFailed(any(), any(), any());
    }

    @Test
    void executeClaimedShouldScheduleRetryForProviderUnavailable() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(1, 3);
        when(inferenceService.create(any())).thenReturn(Map.of(
                "status", "FAILED",
                "errorCode", AiErrorCodes.AI_PROVIDER_UNAVAILABLE,
                "errorMessage", "provider unavailable"));

        service.executeClaimed(task);

        verify(repository).markRetry(any(UUID.class), any(OffsetDateTime.class),
                eq(AiErrorCodes.AI_PROVIDER_UNAVAILABLE), eq("provider unavailable"));
        verify(repository, never()).markFailed(any(), any(), any());
    }

    @Test
    void executeClaimedShouldFailImmediatelyForInvalidProviderResponse() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(1, 3);
        when(inferenceService.create(any())).thenReturn(Map.of(
                "status", "FAILED",
                "errorCode", AiErrorCodes.AI_INVALID_RESPONSE,
                "errorMessage", "invalid response"));

        service.executeClaimed(task);

        verify(repository).markFailed(task.id(), AiErrorCodes.AI_INVALID_RESPONSE, "invalid response");
        verify(repository, never()).markRetry(any(), any(), any(), any());
    }

    @Test
    void executeClaimedShouldFailImmediatelyWhenProviderIsNotConfigured() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(1, 3);
        when(inferenceService.create(any())).thenReturn(Map.of(
                "status", "FAILED",
                "errorCode", AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED,
                "errorMessage", "provider not configured"));

        service.executeClaimed(task);

        verify(repository).markFailed(
                task.id(), AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED, "provider not configured");
        verify(repository, never()).markRetry(any(), any(), any(), any());
    }

    @Test
    void executeClaimedShouldRetryUnexpectedRuntimeFailureBeforeMaximumAttempts() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(1, 3);
        when(inferenceService.create(any())).thenThrow(new IllegalStateException("provider down"));

        service.executeClaimed(task);

        verify(repository).markRetry(any(UUID.class), any(OffsetDateTime.class),
                eq("AI_EXECUTION_UNEXPECTED"), eq("provider down"));
    }

    @Test
    void executeClaimedShouldStopAfterMaximumAttempts() {
        AiExecutionTaskRepository repository = mock(AiExecutionTaskRepository.class);
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiExecutionProperties properties = new AiExecutionProperties();
        AiExecutionTaskService service = new AiExecutionTaskService(repository, inferenceService, properties);
        AiExecutionTask task = task(3, 3);
        when(inferenceService.create(any())).thenThrow(new IllegalStateException("provider down"));

        service.executeClaimed(task);

        verify(repository).markFailed(task.id(), "AI_EXECUTION_UNEXPECTED", "provider down");
    }

    private static AiExecutionCommand command() {
        return new AiExecutionCommand(
                UUID.randomUUID(), "DIFY-IMAGE-ANALYSIS-001", "REAL",
                "AI-DIFY-WORKFLOW-001", "DIFY", "WORKFLOW", null,
                "auto-upload-test", UUID.randomUUID(), Map.of());
    }

    private static AiExecutionTask task(int attempts, int maxAttempts) {
        AiExecutionCommand command = command();
        return new AiExecutionTask(
                UUID.randomUUID(), command.assetId(), command.workflowCode(), command.mode(),
                command.modelId(), command.providerCode(), command.capabilityType(), command.prompt(),
                command.idempotencyKey(), command.requestedBy(), command.inputs(), "RUNNING",
                attempts, maxAttempts, OffsetDateTime.now(), "worker-1",
                OffsetDateTime.now().plusMinutes(1), null, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }
}
