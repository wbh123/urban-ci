package org.urbansafe.priority.ai.governance;

import java.time.OffsetDateTime;

/** 管理员可控的人工智能业务开关。 */
public record AiAutomationSettings(
        boolean autoInferenceOnUpload,
        boolean intelligentWorkflowEnabled,
        boolean knowledgeQaEnabled,
        String modelId,
        String providerCode,
        String capabilityType,
        OffsetDateTime updatedAt) {
}
