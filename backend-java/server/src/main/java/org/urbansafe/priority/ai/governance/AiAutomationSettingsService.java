package org.urbansafe.priority.ai.governance;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.service.AiModelCatalogService;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/** 管理视觉自动识别、智能工作流、知识问答与默认视觉模型。 */
@Service
public class AiAutomationSettingsService {

    /** 历史默认模型，同时作为数据库设置缺失时的安全回退值。 */
    public static final String AUTO_MODEL_ID = "AI-VISION-LOCAL-001";
    public static final String AUTO_PROVIDER_CODE = "FAST_API";
    public static final String AUTO_CAPABILITY_TYPE = "VISION_INFERENCE";
    public static final String WORKFLOW_PROVIDER_CODE = "DIFY";
    public static final String KNOWLEDGE_PROVIDER_CODE = "SPRING_AI";

    private final AiAutomationSettingsRepository repository;
    private final List<AiCapabilityProvider> providers;
    private final AiModelCatalogService modelCatalogService;

    @Autowired
    public AiAutomationSettingsService(
            AiAutomationSettingsRepository repository,
            List<AiCapabilityProvider> providers,
            AiModelCatalogService modelCatalogService) {
        this.repository = repository;
        this.providers = List.copyOf(providers);
        this.modelCatalogService = modelCatalogService;
    }

    /** 测试与旧调用兼容构造器；生产 Spring 容器使用三参数构造器。 */
    public AiAutomationSettingsService(
            AiAutomationSettingsRepository repository,
            List<AiCapabilityProvider> providers) {
        this.repository = repository;
        this.providers = List.copyOf(providers);
        this.modelCatalogService = null;
    }

    public AiAutomationSettings get() {
        return new AiAutomationSettings(
                repository.findAutoInferenceOnUpload(),
                repository.findIntelligentWorkflowEnabled(),
                repository.findKnowledgeQaEnabled(),
                repository.findDefaultVisionModelId(AUTO_MODEL_ID),
                AUTO_PROVIDER_CODE,
                AUTO_CAPABILITY_TYPE,
                repository.findUpdatedAt());
    }

    /**
     * 旧业务入口保持原契约：只切换三个布尔能力，不要求调用方感知默认模型字段。
     */
    public AiAutomationSettings update(
            boolean autoInferenceOnUpload,
            boolean intelligentWorkflowEnabled,
            boolean knowledgeQaEnabled,
            UUID updatedBy) {
        boolean currentAutoInference = repository.findAutoInferenceOnUpload();
        boolean currentWorkflow = repository.findIntelligentWorkflowEnabled();
        boolean currentKnowledgeQa = repository.findKnowledgeQaEnabled();
        validateProviderTransitions(
                autoInferenceOnUpload,
                intelligentWorkflowEnabled,
                knowledgeQaEnabled,
                currentAutoInference,
                currentWorkflow,
                currentKnowledgeQa);
        repository.update(
                autoInferenceOnUpload,
                intelligentWorkflowEnabled,
                knowledgeQaEnabled,
                updatedBy);
        return get();
    }

    /** 管理端新入口：在三个业务开关之外，可安全切换默认视觉模型。 */
    public AiAutomationSettings update(
            boolean autoInferenceOnUpload,
            boolean intelligentWorkflowEnabled,
            boolean knowledgeQaEnabled,
            String requestedModelId,
            UUID updatedBy) {
        boolean currentAutoInference = repository.findAutoInferenceOnUpload();
        boolean currentWorkflow = repository.findIntelligentWorkflowEnabled();
        boolean currentKnowledgeQa = repository.findKnowledgeQaEnabled();
        String currentModelId = repository.findDefaultVisionModelId(AUTO_MODEL_ID);
        String effectiveModelId = normalizeModelId(requestedModelId, currentModelId);

        if ((autoInferenceOnUpload && !currentAutoInference)
                || !effectiveModelId.equals(currentModelId)) {
            requireSelectableVisionModel(effectiveModelId);
        }
        validateProviderTransitions(
                autoInferenceOnUpload,
                intelligentWorkflowEnabled,
                knowledgeQaEnabled,
                currentAutoInference,
                currentWorkflow,
                currentKnowledgeQa);
        repository.update(
                autoInferenceOnUpload,
                intelligentWorkflowEnabled,
                knowledgeQaEnabled,
                effectiveModelId,
                updatedBy);
        return get();
    }

    public boolean intelligentWorkflowEnabled() {
        return repository.findIntelligentWorkflowEnabled();
    }

    public boolean knowledgeQaEnabled() {
        return repository.findKnowledgeQaEnabled();
    }

    private void validateProviderTransitions(
            boolean autoInferenceOnUpload,
            boolean intelligentWorkflowEnabled,
            boolean knowledgeQaEnabled,
            boolean currentAutoInference,
            boolean currentWorkflow,
            boolean currentKnowledgeQa) {
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
    }

    private void requireSelectableVisionModel(String modelId) {
        if (modelCatalogService != null) {
            modelCatalogService.requireSelectableVisionModel(modelId);
        }
    }

    private boolean providerReady(String providerCode, AiCapabilityType capabilityType) {
        return providers.stream().anyMatch(provider ->
                providerCode.equals(normalize(provider.providerCode()))
                        && provider.enabled()
                        && provider.configured()
                        && provider.supports(capabilityType));
    }

    private static String normalizeModelId(String requestedModelId, String currentModelId) {
        if (requestedModelId == null || requestedModelId.isBlank()) {
            return currentModelId;
        }
        return requestedModelId.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
