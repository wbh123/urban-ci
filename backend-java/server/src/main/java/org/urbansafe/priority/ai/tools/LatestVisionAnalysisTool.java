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
            查询楼栋真实（REAL）视觉分析结果（检测数量、状态、复核状态）。
            自动综合研判若已绑定 sourceInferenceId，必须精确复用该刚完成的 REAL 结果；
            只有未绑定 sourceInferenceId 的手动/历史查询才按楼栋读取最近一次成功 REAL 结果。
            只读，不修改任何业务数据。
            """)
    public LatestVisionResult latest(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("LatestVisionAnalysisTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadBuilding(id);

            UUID boundInferenceId = uuid(AiAgentTrace.contextValue("sourceInferenceId"));
            LatestVisionResult result = boundInferenceId == null
                    ? latestByBuilding(id)
                    : exactBoundResult(id, boundInferenceId);
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return result;
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private LatestVisionResult exactBoundResult(UUID buildingId, UUID inferenceId) {
        Map<String, Object> detail = inferenceService.getDetail(inferenceId);
        validateBoundDetail(buildingId, inferenceId, detail);
        return fromDetail(buildingId, inferenceId, detail);
    }

    private LatestVisionResult latestByBuilding(UUID buildingId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("buildingId", buildingId);
        filters.put("mode", "REAL");
        filters.put("status", "SUCCEEDED");
        Map<String, Object> page = inferenceService.list(filters, 0, 1);
        Object rowsValue = page.get("content") != null
                ? page.get("content")
                : page.get("records") != null ? page.get("records") : page.get("list");
        if (!(rowsValue instanceof List<?> rows) || rows.isEmpty()) {
            return new LatestVisionResult(String.valueOf(buildingId), null, null, null, null, null, 0);
        }
        Object row = rows.get(0);
        Map<?, ?> task = row instanceof Map<?, ?> map ? map : Map.of();
        UUID inferenceId = uuid(task.get("inferenceId"));
        if (inferenceId == null) {
            return new LatestVisionResult(
                    String.valueOf(buildingId),
                    null,
                    stringValue(task.get("assetId")),
                    stringValue(task.get("status")),
                    stringValue(task.get("reviewStatus")),
                    stringValue(task.get("modelId")),
                    intValue(task.get("detectionCount")));
        }
        try {
            Map<String, Object> detail = inferenceService.getDetail(inferenceId);
            return fromDetail(buildingId, inferenceId, detail);
        } catch (RuntimeException ignored) {
            return new LatestVisionResult(
                    String.valueOf(buildingId),
                    inferenceId.toString(),
                    stringValue(task.get("assetId")),
                    stringValue(task.get("status")),
                    stringValue(task.get("reviewStatus")),
                    stringValue(task.get("modelId")),
                    intValue(task.get("detectionCount")));
        }
    }

    private static LatestVisionResult fromDetail(
            UUID buildingId, UUID inferenceId, Map<String, Object> detail) {
        return new LatestVisionResult(
                buildingId.toString(),
                inferenceId.toString(),
                stringValue(detail.get("assetId")),
                stringValue(detail.get("status")),
                stringValue(detail.get("reviewStatus")),
                stringValue(detail.get("modelId")),
                intValue(detail.get("detectionCount")));
    }

    private static void validateBoundDetail(
            UUID buildingId, UUID inferenceId, Map<String, Object> detail) {
        UUID detailBuildingId = uuid(detail.get("buildingId"));
        if (!buildingId.equals(detailBuildingId)) {
            throw new IllegalStateException("绑定视觉推理不属于当前楼栋: " + inferenceId);
        }
        if (!"REAL".equalsIgnoreCase(stringValue(detail.get("mode")))) {
            throw new IllegalStateException("绑定视觉推理不是 REAL: " + inferenceId);
        }
        if (!"SUCCEEDED".equalsIgnoreCase(stringValue(detail.get("status")))) {
            throw new IllegalStateException("绑定视觉推理尚未成功: " + inferenceId);
        }
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
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
            String inferenceId,
            String assetId,
            String status,
            String reviewStatus,
            String modelId,
            int detectionCount) {
    }
}
