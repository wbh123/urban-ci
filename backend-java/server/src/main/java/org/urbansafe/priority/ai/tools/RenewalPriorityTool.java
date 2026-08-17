package org.urbansafe.priority.ai.tools;

import java.util.List;
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
            查询楼栋当前城市更新优先级（priorityLevel、优先级分数和相关排序信息）。
            多个排名范围同时存在时优先返回全市/全局 ALL 范围，其次社区、区域。
            在需要了解更新优先级、是否优先治理时使用。只读，不修改任何业务数据。
            """)
    public PriorityResult priority(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("RenewalPriorityTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadFull(id);
            Map<String, Object> current = assessmentService.current(id);
            Map<?, ?> selected = selectPriority(current.get("renewalPriorities"));
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new PriorityResult(
                    stringValue(current.get("buildingId")),
                    stringValue(current.get("buildingName")),
                    stringValue(selected.get("priorityLevel")),
                    stringValue(selected.get("priorityScore")),
                    stringValue(selected.get("ranking")),
                    stringValue(selected.get("rankingScopeKey")),
                    stringValue(selected.get("status")),
                    stringValue(current.get("disclaimer")));
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private static Map<?, ?> selectPriority(Object value) {
        if (!(value instanceof List<?> priorities)) {
            return Map.of();
        }
        Map<?, ?> fallback = Map.of();
        int fallbackRank = Integer.MAX_VALUE;
        for (Object item : priorities) {
            if (!(item instanceof Map<?, ?> candidate)) {
                continue;
            }
            if (!"CURRENT".equalsIgnoreCase(stringValue(candidate.get("status")))) {
                continue;
            }
            String scope = stringValue(candidate.get("rankingScopeKey"));
            if ("ALL".equalsIgnoreCase(scope)) {
                return candidate;
            }
            int rank = scopeRank(scope);
            if (rank < fallbackRank) {
                fallback = candidate;
                fallbackRank = rank;
            }
        }
        return fallback;
    }

    private static int scopeRank(String scope) {
        if (scope == null) return Integer.MAX_VALUE;
        if (scope.startsWith("COMMUNITY:")) return 1;
        if (scope.startsWith("REGION:")) return 2;
        return 3;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record PriorityResult(
            String buildingId,
            String buildingName,
            String priorityLevel,
            String priorityScore,
            String ranking,
            String rankingScopeKey,
            String status,
            String disclaimer) {
    }
}
