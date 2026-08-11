package org.urbansafe.priority.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.common.exception.ResourceConflictException;

class AiAutomationSettingsServiceTest {

    @Test
    void shouldReturnPersistedSwitchAndStableFastApiVisionRoute() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        when(repository.findAutoInferenceOnUpload()).thenReturn(true);
        when(repository.findUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-08-01T12:00:00Z"));

        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository,
                List.of(provider("FAST_API", true, true, Set.of(AiCapabilityType.VISION_INFERENCE))));

        AiAutomationSettings settings = service.get();

        assertThat(settings.autoInferenceOnUpload()).isTrue();
        assertThat(settings.modelId()).isEqualTo("AI-VISION-LOCAL-001");
        assertThat(settings.providerCode()).isEqualTo("FAST_API");
        assertThat(settings.capabilityType()).isEqualTo("VISION_INFERENCE");
    }

    @Test
    void shouldRejectEnablingWhenFastApiVisionProviderIsNotReady() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository,
                List.of(provider("FAST_API", true, false, Set.of(AiCapabilityType.VISION_INFERENCE))));

        assertThatThrownBy(() -> service.update(true, UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("本地视觉模型");
    }

    @Test
    void shouldNotTreatDifyWorkflowAsUploadVisionProvider() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository,
                List.of(provider("DIFY", true, true, Set.of(AiCapabilityType.WORKFLOW))));

        assertThatThrownBy(() -> service.update(true, UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("本地视觉模型");
    }

    @Test
    void shouldAllowDisablingWithoutProbingExternalProvider() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        UUID operatorId = UUID.randomUUID();
        when(repository.findAutoInferenceOnUpload()).thenReturn(false);
        when(repository.findUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-08-01T12:00:00Z"));
        AiAutomationSettingsService service = new AiAutomationSettingsService(repository, List.of());

        AiAutomationSettings settings = service.update(false, operatorId);

        verify(repository).updateAutoInferenceOnUpload(false, operatorId);
        assertThat(settings.autoInferenceOnUpload()).isFalse();
    }

    private static AiCapabilityProvider provider(
            String code,
            boolean enabled,
            boolean configured,
            Set<AiCapabilityType> capabilities) {
        return new AiCapabilityProvider() {
            @Override
            public String providerCode() {
                return code;
            }

            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public boolean configured() {
                return configured;
            }

            @Override
            public Set<AiCapabilityType> capabilities() {
                return capabilities;
            }

            @Override
            public AiStructuredResult execute(AiOrchestrationRequest request) {
                throw new UnsupportedOperationException("not used");
            }
        };
    }
}
