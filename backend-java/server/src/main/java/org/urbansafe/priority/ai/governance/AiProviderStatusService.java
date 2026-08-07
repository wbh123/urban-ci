package org.urbansafe.priority.ai.governance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationProperties;

/**
 * 汇总提供者配置状态与近七日任务统计。
 *
 * <p>本服务不会主动调用外部模型，因此 CONFIGURED 不等于供应商当前在线。
 */
@Service
public class AiProviderStatusService {

    private static final int STATISTICS_DAYS = 7;
    private final List<AiCapabilityProvider> providers;
    private final AiOrchestrationProperties properties;
    private final AiGovernanceRepository repository;

    public AiProviderStatusService(
            List<AiCapabilityProvider> providers,
            AiOrchestrationProperties properties,
            AiGovernanceRepository repository) {
        this.providers = List.copyOf(providers);
        this.properties = properties;
        this.repository = repository;
    }

    public AiGovernanceStatus status() {
        Map<String, AiProviderMetrics> metricsByProvider =
                new LinkedHashMap<>(repository.providerMetrics(STATISTICS_DAYS));
        List<AiProviderStatus> statuses = providers.stream()
                .sorted(Comparator.comparing(AiCapabilityProvider::providerCode))
                .map(provider -> toStatus(provider, metricsByProvider))
                .toList();

        AiProviderMetrics total = AiProviderMetrics.empty();
        for (AiProviderMetrics metrics : metricsByProvider.values()) {
            total = total.plus(metrics);
        }
        long legacy = metricsByProvider
                .getOrDefault("LEGACY", AiProviderMetrics.empty())
                .totalTasks();

        return new AiGovernanceStatus(
                Instant.now(),
                "LAST_7_DAYS",
                statuses,
                total,
                legacy,
                "CONFIGURED 仅表示配置完整；connectivityStatus=NOT_PROBED 表示未主动调用外部服务。",
                "人工智能状态与统计仅用于运维和质量治理，不代表模型准确率、房屋危险概率或正式鉴定结论。");
    }

    private AiProviderStatus toStatus(
            AiCapabilityProvider provider,
            Map<String, AiProviderMetrics> metricsByProvider) {
        String providerCode = normalize(provider.providerCode());
        List<String> capabilities = provider.capabilities().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        List<String> defaultFor = new ArrayList<>();
        for (AiCapabilityType capability : provider.capabilities()) {
            if (providerCode.equals(normalize(properties.defaultProvider(capability)))) {
                defaultFor.add(capability.name());
            }
        }
        defaultFor.sort(String::compareTo);

        String configurationStatus = !provider.enabled()
                ? "DISABLED"
                : provider.configured() ? "CONFIGURED" : "NOT_CONFIGURED";
        return new AiProviderStatus(
                providerCode,
                provider.enabled(),
                provider.configured(),
                configurationStatus,
                "NOT_PROBED",
                capabilities,
                defaultFor,
                metricsByProvider.getOrDefault(providerCode, AiProviderMetrics.empty()));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
