package org.urbansafe.priority.ai.governance;

import java.time.OffsetDateTime;

/** 管理员可控的人工智能自动化设置。 */
public record AiAutomationSettings(
        boolean autoInferenceOnUpload,
        String modelId,
        String providerCode,
        String capabilityType,
        OffsetDateTime updatedAt) {
}
