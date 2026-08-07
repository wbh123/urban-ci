package org.urbansafe.priority.ai.governance;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.type.Alias;

/** 人工智能治理运行状态。 */
@Alias("Phase7AiGovernanceStatus")
public record AiGovernanceStatus(
        Instant generatedAt,
        String statisticsWindow,
        List<AiProviderStatus> providers,
        AiProviderMetrics total7d,
        long unassignedLegacyTasks7d,
        String healthSemantics,
        String disclaimer) {

    public AiGovernanceStatus {
        providers = providers == null ? List.of() : List.copyOf(providers);
        total7d = total7d == null ? AiProviderMetrics.empty() : total7d;
    }
}
