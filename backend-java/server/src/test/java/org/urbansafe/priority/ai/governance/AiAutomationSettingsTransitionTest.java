package org.urbansafe.priority.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;

class AiAutomationSettingsTransitionTest {

    @Test
    void enablingLocalVisionShouldNotRevalidateAlreadyEnabledUnavailableDify() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        when(repository.findAutoInferenceOnUpload()).thenReturn(false, true);
        when(repository.findIntelligentWorkflowEnabled()).thenReturn(true, true);
        when(repository.findKnowledgeQaEnabled()).thenReturn(false, false);
        AiCapabilityProvider fastApi = provider(
                "FAST_API", true, true, Set.of(AiCapabilityType.VISION_INFERENCE));
        AiCapabilityProvider difyUnavailable = provider(
                "DIFY", true, false, Set.of(AiCapabilityType.WORKFLOW));
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository, List.of(fastApi, difyUnavailable));
        UUID operatorId = UUID.randomUUID();

        AiAutomationSettings result = service.update(true, true, false, operatorId);

        verify(repository).update(true, true, false, operatorId);
        assertThat(result.autoInferenceOnUpload()).isTrue();
    }

    @Test
    void enablingDifyShouldStillRequireDifyToBeReady() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        when(repository.findAutoInferenceOnUpload()).thenReturn(false);
        when(repository.findIntelligentWorkflowEnabled()).thenReturn(false);
        when(repository.findKnowledgeQaEnabled()).thenReturn(false);
        AiCapabilityProvider difyUnavailable = provider(
                "DIFY", true, false, Set.of(AiCapabilityType.WORKFLOW));
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository, List.of(difyUnavailable));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.update(false, true, false, UUID.randomUUID()))
                .isInstanceOf(org.urbansafe.priority.common.exception.ResourceConflictException.class)
                .hasMessageContaining("Dify");
    }

    private static AiCapabilityProvider provider(
            String code,
            boolean enabled,
            boolean configured,
            Set<AiCapabilityType> capabilities) {
        return new AiCapabilityProvider() {
            @Override public String providerCode() { return code; }
            @Override public boolean enabled() { return enabled; }
            @Override public boolean configured() { return configured; }
            @Override public Set<AiCapabilityType> capabilities() { return capabilities; }
            @Override public AiStructuredResult execute(AiOrchestrationRequest request) {
                throw new UnsupportedOperationException("not used");
            }
        };
    }
}
