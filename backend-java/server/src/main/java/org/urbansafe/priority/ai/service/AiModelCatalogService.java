package org.urbansafe.priority.ai.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiRuntimeModelInfo;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.repository.AiModelCatalogRepository;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/** 向前端提供经过业务登记与实际运行时双重校验的模型目录。 */
@Service
public class AiModelCatalogService {

    private static final String FAST_API = "FAST_API";

    private final AiModelCatalogRepository repository;
    private final AiFastApiClient fastApiClient;
    private final Map<String, AiCapabilityProvider> providers;

    public AiModelCatalogService(
            AiModelCatalogRepository repository,
            AiFastApiClient fastApiClient,
            List<AiCapabilityProvider> providers) {
        this.repository = repository;
        this.fastApiClient = fastApiClient;
        this.providers = new LinkedHashMap<>();
        for (AiCapabilityProvider provider : providers) {
            this.providers.put(normalize(provider.providerCode()), provider);
        }
    }

    public Map<String, Object> list() {
        List<Map<String, Object>> content = new ArrayList<>();
        for (Map<String, Object> registered : repository.listVisibleModels()) {
            Map<String, Object> item = new LinkedHashMap<>(registered);
            String modelId = String.valueOf(registered.get("modelId"));
            String mode = String.valueOf(registered.get("mode"));
            String providerCode = providerCode(item);
            item.put("providerCode", providerCode);
            if (FAST_API.equals(providerCode)) {
                item.put("capabilityType", capabilityType(item).name());
                enrichFastApiRuntime(item, modelId, mode);
            } else {
                enrichProviderRuntime(item, providerCode);
            }
            content.add(item);
        }
        return Map.of("content", content);
    }

    /**
     * 校验某个模型是否可以成为系统默认视觉模型。
     * 业务 APPROVED 只是第一层门禁；FastAPI 真实运行时未就绪时仍然拒绝切换。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> requireSelectableVisionModel(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new ResourceConflictException("AI_MODEL_NOT_READY", "默认视觉模型编号不能为空");
        }
        List<Map<String, Object>> content = (List<Map<String, Object>>) list().get("content");
        Map<String, Object> model = content.stream()
                .filter(item -> modelId.trim().equals(String.valueOf(item.get("modelId"))))
                .findFirst()
                .orElseThrow(() -> new ResourceConflictException(
                        "AI_MODEL_NOT_READY", "默认视觉模型未登记或不可见"));

        String mode = normalize(String.valueOf(model.get("mode")));
        String status = normalize(String.valueOf(model.get("status")));
        String providerCode = providerCode(model);
        AiCapabilityType capabilityType = capabilityType(model);
        if (!"REAL".equals(mode)
                || !"APPROVED".equals(status)
                || !FAST_API.equals(providerCode)
                || capabilityType != AiCapabilityType.VISION_INFERENCE) {
            throw new ResourceConflictException(
                    "AI_MODEL_NOT_READY", "所选模型不是已批准的 FastAPI 真实视觉模型");
        }
        if (!Boolean.TRUE.equals(model.get("runtimeReady"))
                || !Boolean.TRUE.equals(model.get("selectable"))) {
            String runtimeMessage = model.get("runtimeErrorMessage") == null
                    ? "模型已批准但运行时尚未就绪"
                    : "模型已批准但运行时尚未就绪：" + model.get("runtimeErrorMessage");
            throw new ResourceConflictException("AI_MODEL_NOT_READY", runtimeMessage);
        }
        return model;
    }

    private void enrichFastApiRuntime(Map<String, Object> item, String modelId, String mode) {
        try {
            AiRuntimeModelInfo runtime = fastApiClient.requireModelReady(modelId, mode);
            item.put("runtimeReady", runtime.ready());
            item.put("executionProvider", runtime.executionProvider());
            item.put("deviceId", runtime.deviceId());
            item.put("selectable", runtime.ready());
        } catch (AiFastApiException ex) {
            item.put("runtimeReady", false);
            item.put("executionProvider", null);
            item.put("selectable", false);
            item.put("runtimeErrorCode", ex.getErrorCode());
            item.put("runtimeErrorMessage", ex.getMessage());
        }
    }

    private void enrichProviderRuntime(Map<String, Object> item, String providerCode) {
        AiCapabilityType capabilityType = capabilityType(item);
        item.put("capabilityType", capabilityType.name());
        AiCapabilityProvider provider = providers.get(providerCode);
        if (provider == null) {
            markProviderUnavailable(item, "AI_PROVIDER_NOT_FOUND", "未找到可用的人工智能提供者");
            return;
        }
        if (!provider.enabled()) {
            markProviderUnavailable(item, "AI_PROVIDER_DISABLED", "人工智能提供者未启用");
            return;
        }
        if (!provider.configured()) {
            markProviderUnavailable(item, "AI_PROVIDER_NOT_CONFIGURED", "人工智能提供者尚未完成配置");
            return;
        }
        if (!provider.supports(capabilityType)) {
            markProviderUnavailable(item, "AI_UNSUPPORTED_CAPABILITY", "提供者不支持登记的人工智能能力");
            return;
        }
        item.put("runtimeReady", true);
        item.put("executionProvider", providerCode);
        item.put("deviceId", null);
        item.put("selectable", true);
    }

    private void markProviderUnavailable(Map<String, Object> item, String code, String message) {
        item.put("runtimeReady", false);
        item.put("executionProvider", null);
        item.put("deviceId", null);
        item.put("selectable", false);
        item.put("runtimeErrorCode", code);
        item.put("runtimeErrorMessage", message);
    }

    private static String providerCode(Map<String, Object> item) {
        Object value = item.get("providerCode");
        String providerCode = value == null ? FAST_API : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return providerCode.isBlank() ? FAST_API : providerCode;
    }

    private static AiCapabilityType capabilityType(Map<String, Object> item) {
        Object value = item.get("capabilityType");
        if (value != null && !String.valueOf(value).isBlank()) {
            return AiCapabilityType.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        }
        return String.valueOf(item.get("modelType")).toUpperCase(Locale.ROOT).contains("WORKFLOW")
                ? AiCapabilityType.WORKFLOW : AiCapabilityType.VISION_INFERENCE;
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
