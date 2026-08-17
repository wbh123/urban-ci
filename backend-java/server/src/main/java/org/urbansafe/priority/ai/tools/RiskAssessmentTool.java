package org.urbansafe.priority.ai.tools;

import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;

/** Spring AI 业务只读 Tool：楼栋风险评估摘要。 */
@Component
public class RiskAssessmentTool {

    private final AssessmentApplicationService assessmentService;
    private final AssessmentAccessService accessService;

    public RiskAssessmentTool(
            AssessmentApplicationService assessmentService,
            AssessmentAccessService accessService) {
        this.assessmentService = assessmentService;
        this.accessService = accessService;
    }

    @Tool(description = """
            查询楼栋当前风险评估摘要（风险等级、完整度、免责声明等）。
            在需要了解楼栋风险状况、风险等级或评估结论时使用。只读，不修改任何业务数据。
            """)
    public RiskSummaryResult summary(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("RiskAssessmentTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadFull(id);
            Map<String, Object> summary = assessmentService.summary(id);
            Object risk = summary.get("risk");
            Object completeness = summary.get("completeness");
            Map<?, ?> riskMap = risk instanceof Map<?, ?> map ? map : Map.of();
            Map<?, ?> completenessMap = completeness instanceof Map<?, ?> map ? map : Map.of();
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new RiskSummaryResult(
                    stringValue(summary.get("buildingId")),
                    stringValue(summary.get("buildingName")),
                    stringValue(summary.get("communityName")),
                    stringValue(riskMap.get("riskLevel")),
                    stringValue(riskMap.get("riskScore")),
                    stringValue(completenessMap.get("completenessScore")),
                    stringValue(summary.get("disclaimer")));
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record RiskSummaryResult(
            String buildingId,
            String buildingName,
            String communityName,
            String riskLevel,
            String riskScore,
            String completenessScore,
            String disclaimer) {
    }
}
