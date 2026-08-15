package org.urbansafe.priority.ai.execution;

/** 编排执行步骤状态。 */
public enum AiAgentStepStatus {
    WAITING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
