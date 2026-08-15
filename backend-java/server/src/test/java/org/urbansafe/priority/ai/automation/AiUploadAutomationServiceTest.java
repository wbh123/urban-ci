package org.urbansafe.priority.ai.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.execution.AiExecutionCommand;
import org.urbansafe.priority.ai.execution.AiExecutionTaskService;
import org.urbansafe.priority.ai.governance.AiAutomationSettings;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;

class AiUploadAutomationServiceTest {

    @Test
    void shouldSkipWhenSwitchIsDisabled() {
        AiAutomationSettingsService settingsService = mock(AiAutomationSettingsService.class);
        AiExecutionTaskService taskService = mock(AiExecutionTaskService.class);
        when(settingsService.get()).thenReturn(settings(false));
        AiUploadAutomationService service = new AiUploadAutomationService(settingsService, taskService);

        AiUploadAutomationResult result = service.triggerIfEnabled(
                UUID.randomUUID(), "INSPECTION_TASK", UUID.randomUUID());

        assertThat(result.enabled()).isFalse();
        assertThat(result.triggered()).isFalse();
        assertThat(result.queued()).isFalse();
        verify(taskService, never()).enqueue(any());
    }

    @Test
    void shouldQueueLocalAccuracyWithoutRunningInferenceInUploadThread() {
        AiAutomationSettingsService settingsService = mock(AiAutomationSettingsService.class);
        AiExecutionTaskService taskService = mock(AiExecutionTaskService.class);
        UUID assetId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID executionTaskId = UUID.randomUUID();
        when(settingsService.get()).thenReturn(settings(true));
        when(taskService.enqueue(any())).thenReturn(executionTaskId);
        AiUploadAutomationService service = new AiUploadAutomationService(settingsService, taskService);

        AiUploadAutomationResult result = service.triggerIfEnabled(
                assetId, "INSPECTION_TASK", operatorId);

        ArgumentCaptor<AiExecutionCommand> captor = ArgumentCaptor.forClass(AiExecutionCommand.class);
        verify(taskService).enqueue(captor.capture());
        AiExecutionCommand command = captor.getValue();
        assertThat(command.assetId()).isEqualTo(assetId);
        assertThat(command.workflowCode()).isEqualTo("LOCAL-VISION-ACCURACY-001");
        assertThat(command.mode()).isEqualTo("REAL");
        assertThat(command.modelId()).isEqualTo("AI-VISION-LOCAL-001");
        assertThat(command.providerCode()).isEqualTo("FAST_API");
        assertThat(command.capabilityType()).isEqualTo("VISION_INFERENCE");
        assertThat(command.idempotencyKey()).isEqualTo("auto-upload-" + assetId);
        assertThat(command.requestedBy()).isEqualTo(operatorId);
        assertThat(command.inputs()).containsEntry("inferenceProfile", "ACCURACY");
        assertThat(command.inputs()).containsEntry("triggerType", "UPLOAD_AUTO");
        assertThat(result.triggered()).isTrue();
        assertThat(result.queued()).isTrue();
        assertThat(result.executionTaskId()).isEqualTo(executionTaskId);
        assertThat(result.inferenceId()).isNull();
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void shouldNotRollbackUploadWhenQueueCannotPersistTask() {
        AiAutomationSettingsService settingsService = mock(AiAutomationSettingsService.class);
        AiExecutionTaskService taskService = mock(AiExecutionTaskService.class);
        when(settingsService.get()).thenReturn(settings(true));
        when(taskService.enqueue(any())).thenThrow(new IllegalStateException("database unavailable"));
        AiUploadAutomationService service = new AiUploadAutomationService(settingsService, taskService);

        AiUploadAutomationResult result = service.triggerIfEnabled(
                UUID.randomUUID(), "INSPECTION_TASK", UUID.randomUUID());

        assertThat(result.enabled()).isTrue();
        assertThat(result.triggered()).isFalse();
        assertThat(result.message()).contains("自动识别入队失败");
    }

    @Test
    void shouldNotRollbackUploadWhenAutomationSettingCannotBeRead() {
        AiAutomationSettingsService settingsService = mock(AiAutomationSettingsService.class);
        AiExecutionTaskService taskService = mock(AiExecutionTaskService.class);
        when(settingsService.get()).thenThrow(new IllegalStateException("setting unavailable"));
        AiUploadAutomationService service = new AiUploadAutomationService(settingsService, taskService);

        AiUploadAutomationResult result = service.triggerIfEnabled(
                UUID.randomUUID(), "INSPECTION_TASK", UUID.randomUUID());

        assertThat(result.triggered()).isFalse();
        assertThat(result.message()).contains("设置读取失败");
        verify(taskService, never()).enqueue(any());
    }

    private static AiAutomationSettings settings(boolean enabled) {
        return new AiAutomationSettings(
                enabled,
                false,
                false,
                "AI-VISION-LOCAL-001",
                "FAST_API",
                "VISION_INFERENCE",
                null);
    }
}
