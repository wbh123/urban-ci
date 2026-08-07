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
                enrichFastApiRuntime(item, modelId, mode);
            } else {
                enrichProviderRuntime(item, providerCode);
            }
            content.add(item);
        }
        return Map.of("content", content);
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
