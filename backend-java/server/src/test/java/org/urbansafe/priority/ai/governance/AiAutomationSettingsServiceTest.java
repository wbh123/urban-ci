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
import org.urbansafe.priority.ai.service.AiModelCatalogService;
import org.urbansafe.priority.common.exception.ResourceConflictException;

class AiAutomationSettingsServiceTest {

    @Test
    void shouldReturnPersistedSwitchesAndStableFastApiVisionRoute() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        when(repository.findAutoInferenceOnUpload()).thenReturn(true);
        when(repository.findIntelligentWorkflowEnabled()).thenReturn(true);
        when(repository.findKnowledgeQaEnabled()).thenReturn(true);
        when(repository.findDefaultVisionModelId("AI-VISION-LOCAL-001"))
                .thenReturn("AI-BUILDING-YOLOX-001");
        when(repository.findUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-08-01T12:00:00Z"));

        AiAutomationSettingsService service = new AiAutomationSettingsService(repository, List.of());
        AiAutomationSettings settings = service.get();

        assertThat(settings.autoInferenceOnUpload()).isTrue();
        assertThat(settings.intelligentWorkflowEnabled()).isTrue();
        assertThat(settings.knowledgeQaEnabled()).isTrue();
        assertThat(settings.modelId()).isEqualTo("AI-BUILDING-YOLOX-001");
        assertThat(settings.providerCode()).isEqualTo("FAST_API");
        assertThat(settings.capabilityType()).isEqualTo("VISION_INFERENCE");
    }

    @Test
    void shouldPersistReadyDefaultVisionModel() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiModelCatalogService modelCatalogService = mock(AiModelCatalogService.class);
        UUID operatorId = UUID.randomUUID();
        when(repository.findAutoInferenceOnUpload()).thenReturn(false);
        when(repository.findIntelligentWorkflowEnabled()).thenReturn(false);
        when(repository.findKnowledgeQaEnabled()).thenReturn(false);
        when(repository.findDefaultVisionModelId("AI-VISION-LOCAL-001"))
                .thenReturn("AI-VISION-LOCAL-001", "AI-CRACK-HF-UNET-001");
        when(repository.findUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-08-01T12:00:00Z"));
        when(modelCatalogService.requireSelectableVisionModel("AI-CRACK-HF-UNET-001"))
                .thenReturn(java.util.Map.of("modelId", "AI-CRACK-HF-UNET-001"));
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository, List.of(), modelCatalogService);

        AiAutomationSettings settings = service.update(
                false, false, false, "AI-CRACK-HF-UNET-001", operatorId);

        verify(modelCatalogService).requireSelectableVisionModel("AI-CRACK-HF-UNET-001");
        verify(repository).update(
                false, false, false, "AI-CRACK-HF-UNET-001", operatorId);
        assertThat(settings.modelId()).isEqualTo("AI-CRACK-HF-UNET-001");
    }

    @Test
    void shouldRejectRuntimeUnavailableDefaultVisionModel() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiModelCatalogService modelCatalogService = mock(AiModelCatalogService.class);
        when(repository.findAutoInferenceOnUpload()).thenReturn(false);
        when(repository.findIntelligentWorkflowEnabled()).thenReturn(false);
        when(repository.findKnowledgeQaEnabled()).thenReturn(false);
        when(repository.findDefaultVisionModelId("AI-VISION-LOCAL-001"))
                .thenReturn("AI-VISION-LOCAL-001");
        when(modelCatalogService.requireSelectableVisionModel("AI-BUILDING-YOLOX-001"))
                .thenThrow(new ResourceConflictException(
                        "AI_MODEL_NOT_READY", "模型已批准但运行时尚未就绪"));
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository, List.of(), modelCatalogService);

        assertThatThrownBy(() -> service.update(
                false, false, false, "AI-BUILDING-YOLOX-001", UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("运行时尚未就绪");
    }

    @Test
    void shouldRejectEnablingWhenFastApiVisionProviderIsNotReady() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository,
                List.of(provider("FAST_API", true, false, Set.of(AiCapabilityType.VISION_INFERENCE))));

        assertThatThrownBy(() -> service.update(true, false, false, UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("本地视觉模型");
    }

    @Test
    void shouldRejectWorkflowWhenDifyIsNotReady() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository,
                List.of(provider("DIFY", true, false, Set.of(AiCapabilityType.WORKFLOW))));

        assertThatThrownBy(() -> service.update(false, true, false, UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("Dify");
    }

    @Test
    void shouldRejectKnowledgeQaWhenSpringAiIsNotReady() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        AiAutomationSettingsService service = new AiAutomationSettingsService(
                repository,
                List.of(provider("SPRING_AI", true, false, Set.of(AiCapabilityType.TEXT_GENERATION))));

        assertThatThrownBy(() -> service.update(false, false, true, UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("DeepSeek");
    }

    @Test
    void shouldAllowDisablingAllWithoutExternalProviders() {
        AiAutomationSettingsRepository repository = mock(AiAutomationSettingsRepository.class);
        UUID operatorId = UUID.randomUUID();
        when(repository.findAutoInferenceOnUpload()).thenReturn(false);
        when(repository.findIntelligentWorkflowEnabled()).thenReturn(false);
        when(repository.findKnowledgeQaEnabled()).thenReturn(false);
        when(repository.findDefaultVisionModelId("AI-VISION-LOCAL-001"))
                .thenReturn("AI-VISION-LOCAL-001");
        when(repository.findUpdatedAt()).thenReturn(OffsetDateTime.parse("2026-08-01T12:00:00Z"));
        AiAutomationSettingsService service = new AiAutomationSettingsService(repository, List.of());

        AiAutomationSettings settings = service.update(false, false, false, operatorId);

        verify(repository).update(
                false, false, false, "AI-VISION-LOCAL-001", operatorId);
        assertThat(settings.autoInferenceOnUpload()).isFalse();
        assertThat(settings.intelligentWorkflowEnabled()).isFalse();
        assertThat(settings.knowledgeQaEnabled()).isFalse();
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
