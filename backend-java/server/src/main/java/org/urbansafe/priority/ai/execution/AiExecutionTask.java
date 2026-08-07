package org.urbansafe.priority.ai.execution;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** 已持久化的人工智能执行任务快照。 */
public record AiExecutionTask(
        UUID id,
        UUID assetId,
        String workflowCode,
        String mode,
        String modelId,
        String providerCode,
        String capabilityType,
        String prompt,
        String idempotencyKey,
        UUID requestedBy,
        Map<String, Object> inputs,
        String status,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime availableAt,
        String leaseOwner,
        OffsetDateTime leaseUntil,
        UUID inferenceId,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public AiExecutionTask {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}
