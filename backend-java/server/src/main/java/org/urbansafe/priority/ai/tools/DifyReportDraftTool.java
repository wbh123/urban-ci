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

/** Spring AI Dify Cloud Tool：治理/评估报告草稿工作流（Report Draft）。 */
@Component
public class DifyReportDraftTool {

    private static final String WORKFLOW_CODE = "DIFY-REPORT-DRAFT-001";

    private final DifyWorkflowProvider difyProvider;

    public DifyReportDraftTool(DifyWorkflowProvider difyProvider) {
        this.difyProvider = difyProvider;
    }

    @Tool(description = """
            基于已确认的业务资料生成治理或评估报告草稿。
            仅在需要报告草稿辅助时调用。报告仅为草稿，不是正式专业结论。
            """)
    public DifyToolResult draft(String buildingId, String reportContext) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("DifyReportDraftTool", "DIFY");
        try {
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("businessId", buildingId);
            inputs.put("buildingId", buildingId);
            inputs.put("reportContext", reportContext == null ? "" : reportContext);
            AiOrchestrationRequest request = new AiOrchestrationRequest(
                    UUID.randomUUID().toString(),
                    AiCapabilityType.WORKFLOW,
                    "DIFY",
                    WORKFLOW_CODE,
                    "REAL",
                    null,
                    null,
                    "报告草稿",
                    inputs);
            AiStructuredResult result = difyProvider.execute(request);
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new DifyToolResult(result.modelCode(), result.status(), result.summary(), result.durationMs());
        } catch (AiProviderException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getErrorCode(), ex.getMessage());
            return new DifyToolResult(WORKFLOW_CODE, "UNAVAILABLE",
                    "Dify 报告草稿工作流当前不可用：" + ex.getMessage(), 0L);
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    public record DifyToolResult(String workflowCode, String status, String summary, long durationMs) {
    }
}
