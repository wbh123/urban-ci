package org.urbansafe.priority.ai.governance;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/** 管理上传后自动执行 Dify 工作流的系统开关。 */
@Service
public class AiAutomationSettingsService {

    public static final String AUTO_MODEL_ID = "AI-DIFY-WORKFLOW-001";
    public static final String AUTO_PROVIDER_CODE = "DIFY";
    public static final String AUTO_CAPABILITY_TYPE = "WORKFLOW";

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
                AUTO_MODEL_ID,
                AUTO_PROVIDER_CODE,
                AUTO_CAPABILITY_TYPE,
                repository.findUpdatedAt());
    }

    public AiAutomationSettings update(boolean enabled, UUID updatedBy) {
        if (enabled && !difyWorkflowReady()) {
            throw new ResourceConflictException(
                    "AI_AUTO_INFERENCE_PROVIDER_NOT_READY",
                    "Dify 工作流提供者尚未启用或配置完整，不能开启上传后自动识别");
        }
        repository.updateAutoInferenceOnUpload(enabled, updatedBy);
        return get();
    }

    private boolean difyWorkflowReady() {
        return providers.stream().anyMatch(provider ->
                AUTO_PROVIDER_CODE.equals(normalize(provider.providerCode()))
                        && provider.enabled()
                        && provider.configured()
                        && provider.supports(AiCapabilityType.WORKFLOW));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
