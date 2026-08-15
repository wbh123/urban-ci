package org.urbansafe.priority.ai.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.execution.AiExecutionCommand;
import org.urbansafe.priority.ai.execution.AiExecutionTaskService;
import org.urbansafe.priority.ai.governance.AiAutomationSettings;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;

class AiUploadAutomationAccuracyRoutingTest {

    @Test
    void enabledUploadShouldQueueLocalAccuracyRouteInsteadOfHardCodedDifyWorkflow() {
        AiAutomationSettingsService settingsService = mock(AiAutomationSettingsService.class);
        AiExecutionTaskService taskService = mock(AiExecutionTaskService.class);
        UUID assetId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(settingsService.get()).thenReturn(new AiAutomationSettings(
                true, true, false,
                "AI-VISION-LOCAL-001", "FAST_API", "VISION_INFERENCE", null));
        when(taskService.enqueue(any())).thenReturn(executionId);

        AiUploadAutomationResult result = new AiUploadAutomationService(settingsService, taskService)
                .triggerIfEnabled(assetId, "INSPECTION_TASK", operatorId);

        ArgumentCaptor<AiExecutionCommand> captor = ArgumentCaptor.forClass(AiExecutionCommand.class);
        verify(taskService).enqueue(captor.capture());
        AiExecutionCommand command = captor.getValue();
        assertThat(command.workflowCode()).isEqualTo("LOCAL-VISION-ACCURACY-001");
        assertThat(command.modelId()).isEqualTo("AI-VISION-LOCAL-001");
        assertThat(command.providerCode()).isEqualTo("FAST_API");
        assertThat(command.capabilityType()).isEqualTo("VISION_INFERENCE");
        assertThat(command.inputs()).containsEntry("inferenceProfile", "ACCURACY");
        assertThat(command.inputs()).containsEntry("triggerType", "UPLOAD_AUTO");
        assertThat(result.executionTaskId()).isEqualTo(executionId);
        assertThat(result.status()).isEqualTo("PENDING");
    }
}
