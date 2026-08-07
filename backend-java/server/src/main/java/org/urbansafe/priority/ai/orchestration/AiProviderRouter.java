package org.urbansafe.priority.ai.orchestration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.urbansafe.priority.ai.provider.AiProviderException;

/** 按能力、显式提供者和默认配置选择唯一提供者，不执行隐式降级。 */
public class AiProviderRouter {

    private final Map<String, AiCapabilityProvider> providers;
    private final AiOrchestrationProperties properties;

    public AiProviderRouter(
            List<AiCapabilityProvider> providers,
            AiOrchestrationProperties properties) {
        this.providers = new LinkedHashMap<>();
        for (AiCapabilityProvider provider : providers) {
            this.providers.put(normalize(provider.providerCode()), provider);
        }
        this.properties = properties;
    }

    public AiCapabilityProvider route(AiOrchestrationRequest request) {
        if (request == null || request.capabilityType() == null) {
            throw new AiProviderException(
                    AiErrorCodes.AI_UNSUPPORTED_CAPABILITY, "未指定人工智能能力类型");
        }
        String requestedCode = request.requestedProviderCode();
        String effectiveCode = requestedCode == null || requestedCode.isBlank()
                ? properties.defaultProvider(request.capabilityType())
                : requestedCode;
        AiCapabilityProvider provider = providers.get(normalize(effectiveCode));
        if (provider == null) {
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_NOT_FOUND, "未找到可用的人工智能提供者");
        }
        if (!provider.enabled()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_DISABLED, "人工智能提供者未启用");
        }
        if (!provider.configured()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED, "人工智能提供者尚未完成配置");
        }
        if (!provider.supports(request.capabilityType())) {
            throw new AiProviderException(
                    AiErrorCodes.AI_UNSUPPORTED_CAPABILITY, "提供者不支持请求的人工智能能力");
        }
        return provider;
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
