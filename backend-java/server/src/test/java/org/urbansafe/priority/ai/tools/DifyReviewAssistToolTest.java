package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.provider.DifyWorkflowProvider;

/** Dify 复核辅助 Tool：不可用时返回结构化错误结果并记录 FAILED 步骤，不抛异常。 */
class DifyReviewAssistToolTest {

    private AiAgentExecution execution;

    @BeforeEach
    void beginTrace() {
        execution = new AiAgentExecution(
                UUID.randomUUID(), "BUILDING", UUID.randomUUID(), "综合分析", UUID.randomUUID(), "t");
        AiAgentTrace.begin(execution);
    }

    @AfterEach
    void endTrace() {
        AiAgentTrace.end();
    }

    @Test
    void returnsUnavailableWhenDifyDisabledAndRecordsFailedStep() {
        DifyWorkflowProvider provider = mock(DifyWorkflowProvider.class);
        when(provider.execute(any())).thenThrow(new AiProviderException(
                AiErrorCodes.AI_PROVIDER_DISABLED, "Dify 工作流未启用"));

        DifyReviewAssistTool tool = new DifyReviewAssistTool(provider);
        DifyReviewAssistTool.DifyToolResult result = tool.run(
                UUID.randomUUID().toString(), "分析摘要");

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.summary()).contains("不可用");
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("DifyReviewAssistTool")
                        && step.status() == AiAgentStepStatus.FAILED);
    }

    @Test
    void returnsSuccessWhenDifyAvailable() {
        DifyWorkflowProvider provider = mock(DifyWorkflowProvider.class);
        when(provider.execute(any())).thenReturn(new AiOrchestrationResult(
                "req", "DIFY", "DIFY-REVIEW-ASSIST-001", "v1", null,
                "SUCCEEDED", "复核辅助完成", java.util.List.of(), java.util.List.of(),
                java.util.List.of(), 0.8d, java.util.List.of(), "dify:run-1", 100L));

        DifyReviewAssistTool tool = new DifyReviewAssistTool(provider);
        DifyReviewAssistTool.DifyToolResult result = tool.run(
                UUID.randomUUID().toString(), "分析摘要");

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("DifyReviewAssistTool")
                        && step.status() == AiAgentStepStatus.SUCCEEDED);
    }
}
