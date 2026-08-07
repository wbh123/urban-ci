package org.urbansafe.priority.ai.governance;

import java.util.List;
import org.apache.ibatis.type.Alias;

/** 管理员可见的提供者状态，只描述配置与历史任务，不执行外部连通性探测。 */
@Alias("Phase7AiProviderStatus")
public record AiProviderStatus(
        String providerCode,
        boolean enabled,
        boolean configured,
        String configurationStatus,
        String connectivityStatus,
        List<String> capabilities,
        List<String> defaultFor,
        AiProviderMetrics metrics7d) {

    public AiProviderStatus {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        defaultFor = defaultFor == null ? List.of() : List.copyOf(defaultFor);
        metrics7d = metrics7d == null ? AiProviderMetrics.empty() : metrics7d;
    }
}
