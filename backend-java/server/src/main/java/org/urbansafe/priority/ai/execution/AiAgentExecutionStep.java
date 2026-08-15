package org.urbansafe.priority.ai.execution;

import java.time.Instant;

/** 编排执行中的单个步骤（Tool 或 LLM 调用）。 */
public record AiAgentExecutionStep(
        int seqNo,
        AiAgentStepType type,
        String toolName,
        String provider,
        AiAgentStepStatus status,
        Long durationMs,
        String errorCode,
        String detail,
        Instant createdAt) {

    public AiAgentExecutionStep {
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
