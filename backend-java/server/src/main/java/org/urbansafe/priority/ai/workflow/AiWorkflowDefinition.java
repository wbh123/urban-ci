package org.urbansafe.priority.ai.workflow;

import java.util.Map;

/** 工作流登记与运行配置的合并视图，不向外暴露密钥。 */
public record AiWorkflowDefinition(
        String workflowCode,
        String modelCode,
        String displayName,
        String providerCode,
        String capabilityType,
        String configKey,
        String currentVersion,
        String inputSchemaVersion,
        String outputSchemaVersion,
        boolean enabled,
        String qualityStatus,
        boolean formalEvidenceEnabled,
        int timeoutMs,
        int maxAttempts,
        Map<String, Object> dataPolicy,
        String apiKey,
        String appId,
        boolean configured) {

    public AiWorkflowDefinition {
        dataPolicy = dataPolicy == null ? Map.of() : Map.copyOf(dataPolicy);
    }
}
