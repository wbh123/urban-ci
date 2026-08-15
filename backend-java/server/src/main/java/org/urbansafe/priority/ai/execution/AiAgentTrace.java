package org.urbansafe.priority.ai.execution;

/**
 * 当前请求的编排执行轨迹（ThreadLocal）。
 *
 * <p>Spring AI Tool Calling 在同步请求线程内执行工具方法，因此用 ThreadLocal 让
 * Tool 无需感知编排器即可记录步骤。编排器在请求开始/结束时 begin/end。
 *
 * <p>Tool 用 beginStep/finishStep 控制自身步骤状态：预期能力失败（如 Dify 未配置、
 * 视觉不可用）应记录 FAILED 并返回结构化错误结果，而不是抛异常中断整体编排。
 */
public final class AiAgentTrace {

    private static final ThreadLocal<AiAgentExecution> CURRENT = new ThreadLocal<>();

    private AiAgentTrace() {
    }

    public static void begin(AiAgentExecution execution) {
        CURRENT.set(execution);
    }

    public static AiAgentExecution current() {
        return CURRENT.get();
    }

    public static void end() {
        CURRENT.remove();
    }

    /** 开始一个工具步骤；Tool 在返回/捕获后调用 finishStep 完成。 */
    public static StepToken beginStep(String toolName, String provider) {
        AiAgentExecution execution = CURRENT.get();
        int seq = execution == null ? 1 : execution.nextSeq();
        return new StepToken(execution, seq, toolName, provider, System.nanoTime());
    }

    public static void finishStep(StepToken token, AiAgentStepStatus status, String errorCode, String detail) {
        if (token == null || token.execution() == null) {
            return;
        }
        long durationMs = Math.max(0L, (System.nanoTime() - token.startedNanos()) / 1_000_000L);
        token.execution().addStep(new AiAgentExecutionStep(
                token.seq(),
                AiAgentStepType.TOOL,
                token.toolName(),
                token.provider(),
                status,
                durationMs,
                errorCode,
                detail,
                null));
    }

    public static void recordLlm(String modelCode, long durationMs) {
        AiAgentExecution execution = CURRENT.get();
        if (execution == null) {
            return;
        }
        execution.setModelCode(modelCode);
        execution.addStep(new AiAgentExecutionStep(
                execution.nextSeq(),
                AiAgentStepType.LLM,
                "DeepSeek",
                "SPRING_AI",
                AiAgentStepStatus.SUCCEEDED,
                durationMs,
                null,
                "DeepSeek response generated",
                null));
    }

    public record StepToken(
            AiAgentExecution execution,
            int seq,
            String toolName,
            String provider,
            long startedNanos) {
    }
}
