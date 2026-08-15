package org.urbansafe.priority.ai.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.provider.DifyWorkflowProvider;

/** Spring AI Dify Cloud Tool：专业复核辅助工作流（Review Assist）。 */
@Component
public class DifyReviewAssistTool {

    private static final String WORKFLOW_CODE = "DIFY-REVIEW-ASSIST-001";

    private final DifyWorkflowProvider difyProvider;

    public DifyReviewAssistTool(DifyWorkflowProvider difyProvider) {
        this.difyProvider = difyProvider;
    }

    @Tool(description = """
            根据已经获取的视觉分析、巡检证据和楼栋业务信息，执行专业复核辅助工作流。
            仅在需要复杂专业复核辅助时调用。该工具不可用时不应阻塞人工复核。
            """)
    public DifyToolResult run(String buildingId, String analysisSummary) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("DifyReviewAssistTool", "DIFY");
        try {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("businessId", buildingId);
            inputs.put("buildingId", buildingId);
            inputs.put("analysisSummary", analysisSummary == null ? "" : analysisSummary);
            AiOrchestrationRequest request = new AiOrchestrationRequest(
                    UUID.randomUUID().toString(),
                    AiCapabilityType.WORKFLOW,
                    "DIFY",
                    WORKFLOW_CODE,
                    "REAL",
                    null,
                    null,
                    "专业复核辅助",
                    inputs);
            AiStructuredResult result = difyProvider.execute(request);
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new DifyToolResult(result.modelCode(), result.status(), result.summary(), result.durationMs());
        } catch (AiProviderException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getErrorCode(), ex.getMessage());
            return new DifyToolResult(WORKFLOW_CODE, "UNAVAILABLE",
                    "Dify 复核辅助工作流当前不可用：" + ex.getMessage(), 0L);
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    public record DifyToolResult(String workflowCode, String status, String summary, long durationMs) {
    }
}
