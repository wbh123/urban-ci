package org.urbansafe.priority.ai.execution;

/** Spring AI 智能编排执行状态。 */
public enum AiAgentExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    PARTIAL_SUCCEEDED,
    FAILED
}
