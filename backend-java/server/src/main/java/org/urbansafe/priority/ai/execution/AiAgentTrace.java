package org.urbansafe.priority.ai.execution;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前请求的编排执行轨迹（ThreadLocal）。
 *
 * <p>Spring AI Tool Calling 在同步请求线程内执行工具方法，因此用 ThreadLocal 让
 * Tool 无需感知编排器即可记录步骤。编排器在请求开始/结束时 begin/end。
 *
 * <p>除执行轨迹外，还保存当前编排的受控业务上下文。自动综合研判可通过
 * sourceInferenceId 将后续 Tool 强绑定到刚完成的 REAL 视觉推理，避免按楼栋“取最新”时
 * 误选演示种子或其他历史结果。
 *
 * <p>Tool 用 beginStep/finishStep 控制自身步骤状态：预期能力失败（如 Dify 未配置、
 * 视觉不可用）应记录 FAILED 并返回结构化错误结果，而不是抛异常中断整体编排。
 */
public final class AiAgentTrace {

    private static final ThreadLocal<AiAgentExecution> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> CONTEXT = new ThreadLocal<>();

    private AiAgentTrace() {
    }

    /**
     * 开始执行轨迹。若异步执行器已经通过 bindContext 绑定了受控业务上下文，则保留该上下文；
     * 否则初始化为空上下文。
     */
    public static void begin(AiAgentExecution execution) {
        CURRENT.set(execution);
        if (CONTEXT.get() == null) {
            CONTEXT.set(Map.of());
        }
    }

    public static void begin(AiAgentExecution execution, Map<String, Object> context) {
        CURRENT.set(execution);
        bindContext(context);
    }

    /** 在进入 Spring AI 编排前绑定当前任务的受控业务上下文。 */
    public static void bindContext(Map<String, Object> context) {
        CONTEXT.set(context == null ? Map.of() : new LinkedHashMap<>(context));
    }

    public static AiAgentExecution current() {
        return CURRENT.get();
    }

    public static Object contextValue(String key) {
        Map<String, Object> context = CONTEXT.get();
        return context == null || key == null ? null : context.get(key);
    }

    public static void clearContext() {
        CONTEXT.remove();
    }

    public static void end() {
        CURRENT.remove();
        CONTEXT.remove();
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
