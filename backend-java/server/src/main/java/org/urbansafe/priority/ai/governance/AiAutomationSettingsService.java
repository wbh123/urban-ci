package org.urbansafe.priority.ai.governance;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/** 管理视觉自动识别、智能工作流与知识问答的业务开关。 */
@Service
public class AiAutomationSettingsService {

    public static final String AUTO_MODEL_ID = "AI-VISION-LOCAL-001";
    public static final String AUTO_PROVIDER_CODE = "FAST_API";
    public static final String AUTO_CAPABILITY_TYPE = "VISION_INFERENCE";
    public static final String WORKFLOW_PROVIDER_CODE = "DIFY";
    public static final String KNOWLEDGE_PROVIDER_CODE = "SPRING_AI";

    private final AiAutomationSettingsRepository repository;
    private final List<AiCapabilityProvider> providers;

    public AiAutomationSettingsService(
            AiAutomationSettingsRepository repository,
            List<AiCapabilityProvider> providers) {
        this.repository = repository;
        this.providers = List.copyOf(providers);
    }

    public AiAutomationSettings get() {
        return new AiAutomationSettings(
                repository.findAutoInferenceOnUpload(),
                repository.findIntelligentWorkflowEnabled(),
                repository.findKnowledgeQaEnabled(),
                AUTO_MODEL_ID,
                AUTO_PROVIDER_CODE,
                AUTO_CAPABILITY_TYPE,
                repository.findUpdatedAt());
    }

    public AiAutomationSettings update(
            boolean autoInferenceOnUpload,
            boolean intelligentWorkflowEnabled,
            boolean knowledgeQaEnabled,
            UUID updatedBy) {
        boolean currentAutoInference = repository.findAutoInferenceOnUpload();
        boolean currentWorkflow = repository.findIntelligentWorkflowEnabled();
        boolean currentKnowledgeQa = repository.findKnowledgeQaEnabled();

        if (autoInferenceOnUpload
                && !currentAutoInference
                && !providerReady(AUTO_PROVIDER_CODE, AiCapabilityType.VISION_INFERENCE)) {
            throw new ResourceConflictException(
                    "AI_AUTO_INFERENCE_PROVIDER_NOT_READY",
                    "本地视觉模型提供者尚未启用或配置完整，不能开启上传后自动识别");
        }
        if (intelligentWorkflowEnabled
                && !currentWorkflow
                && !providerReady(WORKFLOW_PROVIDER_CODE, AiCapabilityType.WORKFLOW)) {
            throw new ResourceConflictException(
                    "AI_WORKFLOW_PROVIDER_NOT_READY",
                    "Dify 智能工作流尚未启用或配置完整，不能开启智能工作流");
        }
        if (knowledgeQaEnabled
                && !currentKnowledgeQa
                && !providerReady(KNOWLEDGE_PROVIDER_CODE, AiCapabilityType.TEXT_GENERATION)) {
            throw new ResourceConflictException(
                    "AI_KNOWLEDGE_PROVIDER_NOT_READY",
                    "Spring AI / DeepSeek 文本能力尚未启用或配置完整，不能开启知识问答");
        }
        repository.update(
                autoInferenceOnUpload,
                intelligentWorkflowEnabled,
                knowledgeQaEnabled,
                updatedBy);
        return get();
    }

    public boolean intelligentWorkflowEnabled() {
        return repository.findIntelligentWorkflowEnabled();
    }

    public boolean knowledgeQaEnabled() {
        return repository.findKnowledgeQaEnabled();
    }

    private boolean providerReady(String providerCode, AiCapabilityType capabilityType) {
        return providers.stream().anyMatch(provider ->
                providerCode.equals(normalize(provider.providerCode()))
                        && provider.enabled()
                        && provider.configured()
                        && provider.supports(capabilityType));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
