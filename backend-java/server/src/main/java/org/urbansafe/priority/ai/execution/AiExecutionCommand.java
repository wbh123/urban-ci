package org.urbansafe.priority.ai.execution;

import java.util.Map;
import java.util.UUID;

/** 创建可恢复人工智能执行任务的稳定命令。 */
public record AiExecutionCommand(
        UUID assetId,
        String workflowCode,
        String mode,
        String modelId,
        String providerCode,
        String capabilityType,
        String prompt,
        String idempotencyKey,
        UUID requestedBy,
        Map<String, Object> inputs) {

    public AiExecutionCommand {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}
