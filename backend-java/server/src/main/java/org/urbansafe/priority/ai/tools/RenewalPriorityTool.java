package org.urbansafe.priority.ai.tools;

import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;

/** Spring AI 业务只读 Tool：楼栋城市更新优先级。 */
@Component
public class RenewalPriorityTool {

    private final AssessmentApplicationService assessmentService;
    private final AssessmentAccessService accessService;

    public RenewalPriorityTool(
            AssessmentApplicationService assessmentService,
            AssessmentAccessService accessService) {
        this.assessmentService = assessmentService;
        this.accessService = accessService;
    }

    @Tool(description = """
            查询楼栋当前城市更新优先级（priorityLevel、相关排序信息）。
            在需要了解更新优先级、是否优先治理时使用。只读，不修改任何业务数据。
            """)
    public PriorityResult priority(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("RenewalPriorityTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadFull(id);
            Map<String, Object> summary = assessmentService.summary(id);
            Object risk = summary.get("risk");
            Map<?, ?> riskMap = risk instanceof Map<?, ?> map ? map : Map.of();
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new PriorityResult(
                    stringValue(summary.get("buildingId")),
                    stringValue(summary.get("buildingName")),
                    stringValue(riskMap.get("priorityLevel")),
                    stringValue(summary.get("disclaimer")));
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record PriorityResult(
            String buildingId,
            String buildingName,
            String priorityLevel,
            String disclaimer) {
    }
}
