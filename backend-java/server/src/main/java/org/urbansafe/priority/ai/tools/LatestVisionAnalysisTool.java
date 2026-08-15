package org.urbansafe.priority.ai.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.common.security.BusinessAccessService;

/** Spring AI 业务只读 Tool：楼栋最近一次真实视觉分析结果。 */
@Component
public class LatestVisionAnalysisTool {

    private final AiInferenceService inferenceService;
    private final BusinessAccessService accessService;

    public LatestVisionAnalysisTool(
            AiInferenceService inferenceService,
            BusinessAccessService accessService) {
        this.inferenceService = inferenceService;
        this.accessService = accessService;
    }

    @Tool(description = """
            查询楼栋最近一次真实（REAL）视觉分析结果（检测数量、状态、复核状态）。
            在需要了解该楼栋是否有历史视觉分析结论时使用。只读，不修改任何业务数据。
            """)
    public LatestVisionResult latest(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("LatestVisionAnalysisTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadBuilding(id);
            Map<String, Object> filters = new LinkedHashMap<>();
            filters.put("buildingId", id);
            filters.put("mode", "REAL");
            Map<String, Object> page = inferenceService.list(filters, 0, 1);
            Object rowsValue = page.get("records") != null ? page.get("records") : page.get("list");
            if (!(rowsValue instanceof List<?> rows) || rows.isEmpty()) {
                AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
                return new LatestVisionResult(String.valueOf(id), null, null, null, 0);
            }
            Object row = rows.get(0);
            Map<?, ?> task = row instanceof Map<?, ?> map ? map : Map.of();
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new LatestVisionResult(
                    String.valueOf(id),
                    stringValue(task.get("status")),
                    stringValue(task.get("reviewStatus")),
                    stringValue(task.get("modelId")),
                    intValue(task.get("detectionCount")));
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record LatestVisionResult(
            String buildingId,
            String status,
            String reviewStatus,
            String modelId,
            int detectionCount) {
    }
}
