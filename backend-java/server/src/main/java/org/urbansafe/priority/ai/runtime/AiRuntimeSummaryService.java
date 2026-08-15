package org.urbansafe.priority.ai.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.governance.AiProviderProbeService;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;

/** 面向普通管理端页面的业务化 AI 运行摘要，不暴露治理统计、技术错误码或配置细节。 */
@Service
public class AiRuntimeSummaryService {

    private static final String POLICY = "Dify 优先 / 本地兜底";

    private final List<AiCapabilityProvider> providers;
    private final AiProviderProbeService probeService;

    public AiRuntimeSummaryService(
            List<AiCapabilityProvider> providers,
            AiProviderProbeService probeService) {
        this.providers = List.copyOf(providers);
        this.probeService = probeService;
    }

    public Map<String, Object> summary() {
        RuntimeState vision = runtimeState("FAST_API", AiCapabilityType.VISION_INFERENCE);
        RuntimeState workflow = runtimeState("DIFY", AiCapabilityType.WORKFLOW);
        RuntimeState spring = runtimeState("SPRING_AI", AiCapabilityType.TEXT_GENERATION);

        List<Map<String, Object>> services = List.of(
                service("vision", "本地视觉", vision),
                service("workflow", "智能工作流", workflow),
                service("orchestration", "本地编排", spring),
                service("knowledge", "知识服务", spring));

        String state;
        if (!vision.usable() && !spring.usable()) {
            state = "UNAVAILABLE";
        } else if (vision.ready() && workflow.ready() && spring.ready()) {
            state = "READY";
        } else {
            state = "DEGRADED";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now());
        result.put("state", state);
        result.put("services", services);
        result.put("policy", POLICY);
        return result;
    }

    private RuntimeState runtimeState(String providerCode, AiCapabilityType capability) {
        AiCapabilityProvider provider = provider(providerCode);
        if (provider == null
                || !provider.enabled()
                || !provider.configured()
                || provider.capabilities() == null
                || !provider.capabilities().contains(capability)) {
            return RuntimeState.unavailable();
        }

        String runtimeStatus;
        try {
            runtimeStatus = probeService.probe(providerCode).runtimeStatus();
        } catch (RuntimeException ex) {
            runtimeStatus = "UNAVAILABLE";
        }
        return switch (runtimeStatus == null ? "" : runtimeStatus.trim().toUpperCase(Locale.ROOT)) {
            case "READY" -> new RuntimeState("正常", true, true);
            case "DEGRADED" -> new RuntimeState("降级可用", true, false);
            default -> RuntimeState.unavailable();
        };
    }

    private AiCapabilityProvider provider(String code) {
        return providers.stream()
                .filter(item -> code.equalsIgnoreCase(item.providerCode()))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> service(String key, String label, RuntimeState runtime) {
        return Map.of(
                "key", key,
                "label", label,
                "status", runtime.label());
    }

    private record RuntimeState(String label, boolean usable, boolean ready) {
        static RuntimeState unavailable() {
            return new RuntimeState("不可用", false, false);
        }
    }
}
