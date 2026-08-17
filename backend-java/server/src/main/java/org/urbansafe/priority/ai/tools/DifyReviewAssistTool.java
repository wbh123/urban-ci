package org.urbansafe.priority.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

/** Spring AI Dify Cloud Tool：专业复核辅助工作流（Review Assist）。 */
@Component
public class DifyReviewAssistTool {

    private static final String WORKFLOW_CODE = "DIFY-REVIEW-ASSIST-001";
    private static final int MAX_DETECTIONS_FOR_REVIEW = 20;

    private final DifyWorkflowProvider difyProvider;
    private final BusinessAccessService accessService;
    private final BuildingService buildingService;
    private final Phase2InspectionService inspectionService;
    private final AiInferenceService inferenceService;
    private final ObjectMapper objectMapper;

    public DifyReviewAssistTool(
            DifyWorkflowProvider difyProvider,
            BusinessAccessService accessService,
            BuildingService buildingService,
            Phase2InspectionService inspectionService,
            AiInferenceService inferenceService,
            ObjectMapper objectMapper) {
        this.difyProvider = difyProvider;
        this.accessService = accessService;
        this.buildingService = buildingService;
        this.inspectionService = inspectionService;
        this.inferenceService = inferenceService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "dify_review_assist", description = """
            对指定楼栋执行 Dify Review Assist 专业复核辅助。
            Java 会在当前认证用户权限范围内自行读取楼栋档案、巡检记录和 REAL 视觉结果。
            自动综合研判若已绑定 sourceInferenceId，必须精确复用该刚完成的 REAL 结果；
            只有没有绑定 sourceInferenceId 的手动研判才按楼栋回退到最近一次成功 REAL 结果。
            用于整理证据一致项、冲突项、缺失字段、补拍需求和待专家回答的问题。
            不修改业务数据库，不重新计算正式风险分，不代替专家结论。
            """)
    public DifyToolResult run(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("DifyReviewAssistTool", "DIFY");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadBuilding(id);
            UUID sourceInferenceId = uuid(AiAgentTrace.contextValue("sourceInferenceId"));

            BuildingDetailResult building = buildingService.getBuilding(id);
            Map<String, Object> buildingContext = buildingContext(building);
            if (sourceInferenceId != null) {
                buildingContext.put("sourceInferenceId", sourceInferenceId.toString());
            }
            Map<String, Object> inspectionContext = inspectionContext(id);
            Map<String, Object> latestVision = latestRealVision(id, sourceInferenceId);
            Map<String, Object> analysisContext = analysisContext(latestVision);
            Map<String, Object> localModelContext = localModelContext(latestVision);

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("analysisJson", json(analysisContext));
            inputs.put("inspectionRecordJson", json(inspectionContext));
            inputs.put("localModelJson", json(localModelContext));
            inputs.put("buildingContextJson", json(buildingContext));

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
            return new DifyToolResult(
                    result.modelCode(), result.status(), result.summary(), result.recommendations(),
                    result.warnings(), result.durationMs());
        } catch (AiProviderException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getErrorCode(), ex.getMessage());
            return new DifyToolResult(
                    WORKFLOW_CODE, "UNAVAILABLE",
                    "Dify 复核辅助工作流当前不可用：" + ex.getMessage(),
                    List.of(), List.of(ex.getErrorCode()), 0L);
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private Map<String, Object> inspectionContext(UUID buildingId) {
        List<Map<String, Object>> tasks = inspectionService.listTasks(buildingId, null);
        int recordCount = 0;
        for (Map<String, Object> task : tasks) {
            UUID taskId = uuid(task.get("taskId"));
            if (taskId != null) {
                recordCount += inspectionService.listRecords(taskId).size();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId.toString());
        result.put("inspectionTaskCount", tasks.size());
        result.put("inspectionRecordCount", recordCount);
        result.put("note", "由 Spring Boot 在当前认证用户权限范围内实时汇总");
        return result;
    }

    private Map<String, Object> latestRealVision(UUID buildingId, UUID sourceInferenceId) {
        if (sourceInferenceId != null) {
            Map<String, Object> detail = new LinkedHashMap<>(inferenceService.getDetail(sourceInferenceId));
            validateBoundVision(buildingId, sourceInferenceId, detail);
            return detail;
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("buildingId", buildingId);
        filters.put("mode", "REAL");
        filters.put("status", "SUCCEEDED");
        Map<String, Object> page = inferenceService.list(filters, 0, 1);
        Object content = page.get("content");
        if (!(content instanceof List<?> rows) || rows.isEmpty()) {
            return Map.of(
                    "buildingId", buildingId.toString(),
                    "status", "NO_REAL_VISION_RESULT");
        }
        Object first = rows.get(0);
        if (!(first instanceof Map<?, ?> row)) {
            return Map.of(
                    "buildingId", buildingId.toString(),
                    "status", "NO_REAL_VISION_RESULT");
        }
        UUID inferenceId = uuid(row.get("inferenceId"));
        if (inferenceId == null) {
            return Map.of(
                    "buildingId", buildingId.toString(),
                    "status", "NO_REAL_VISION_RESULT");
        }
        return new LinkedHashMap<>(inferenceService.getDetail(inferenceId));
    }

    private static void validateBoundVision(
            UUID buildingId, UUID sourceInferenceId, Map<String, Object> detail) {
        UUID detailBuildingId = uuid(detail.get("buildingId"));
        if (!buildingId.equals(detailBuildingId)) {
            throw new IllegalStateException("绑定视觉推理不属于当前楼栋: " + sourceInferenceId);
        }
        if (!"REAL".equalsIgnoreCase(string(detail.get("mode")))) {
            throw new IllegalStateException("绑定视觉推理不是 REAL: " + sourceInferenceId);
        }
        if (!"SUCCEEDED".equalsIgnoreCase(string(detail.get("status")))) {
            throw new IllegalStateException("绑定视觉推理尚未成功: " + sourceInferenceId);
        }
    }

    private static Map<String, Object> analysisContext(Map<String, Object> vision) {
        Map<String, Object> result = baseVisionContext(vision);
        copyIfPresent(vision, result, "reviewStatus");
        copyIfPresent(vision, result, "qualityStatus");
        copyIfPresent(vision, result, "applicability");
        copyIfPresent(vision, result, "assessmentEligibility");
        copyIfPresent(vision, result, "assessmentNote");
        copyIfPresent(vision, result, "warnings");
        copyIfPresent(vision, result, "summary");
        List<Map<String, Object>> detections = canonicalDetections(vision);
        result.put("detections", detections);
        result.put("detectionCount", detections.size());
        return result;
    }

    private static Map<String, Object> localModelContext(Map<String, Object> vision) {
        Map<String, Object> result = baseVisionContext(vision);
        List<Map<String, Object>> detections = canonicalDetections(vision);
        result.put("detections", detections);
        result.put("detectionCount", detections.size());
        Object structured = vision.get("structuredResult");
        if (structured instanceof Map<?, ?> map) {
            Map<String, Object> compactStructured = new LinkedHashMap<>();
            copyMapValue(map, compactStructured, "summary");
            copyMapValue(map, compactStructured, "riskSignals");
            copyMapValue(map, compactStructured, "recommendations");
            copyMapValue(map, compactStructured, "confidence");
            compactStructured.put("detections", detections);
            result.put("structuredResult", compactStructured);
        } else {
            result.put("structuredResult", Map.of("detections", detections));
        }
        return result;
    }

    /** 统一检测列表：优先结构化结果非空明细，否则回退持久化明细，避免 Dify 两个输入冲突。 */
    private static List<Map<String, Object>> canonicalDetections(Map<String, Object> vision) {
        Object structured = vision.get("structuredResult");
        if (structured instanceof Map<?, ?> map
                && map.get("detections") instanceof List<?> structuredDetections
                && !structuredDetections.isEmpty()) {
            return compactDetections(structuredDetections);
        }
        return compactDetections(vision.get("detections"));
    }

    private static Map<String, Object> baseVisionContext(Map<String, Object> vision) {
        Map<String, Object> result = new LinkedHashMap<>();
        copyIfPresent(vision, result, "inferenceId");
        copyIfPresent(vision, result, "requestCode");
        copyIfPresent(vision, result, "mode");
        copyIfPresent(vision, result, "status");
        copyIfPresent(vision, result, "modelId");
        copyIfPresent(vision, result, "modelVersion");
        copyIfPresent(vision, result, "detectionCount");
        return result;
    }

    private static List<Map<String, Object>> compactDetections(Object raw) {
        if (!(raw instanceof List<?> detections) || detections.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : detections) {
            if (result.size() >= MAX_DETECTIONS_FOR_REVIEW) break;
            if (!(item instanceof Map<?, ?> detection)) continue;
            Map<String, Object> compact = new LinkedHashMap<>();
            copyMapValue(detection, compact, "sequence");
            copyMapValue(detection, compact, "classCode");
            copyMapValue(detection, compact, "className");
            copyMapValue(detection, compact, "confidence");
            copyMapValue(detection, compact, "boundingBox");
            copyMapValue(detection, compact, "trustLevel");
            copyMapValue(detection, compact, "trustReasons");
            Object segmentation = detection.get("segmentation");
            if (segmentation instanceof Map<?, ?> segmentationMap && segmentationMap.get("type") != null) {
                compact.put("segmentationType", segmentationMap.get("type"));
            }
            result.add(compact);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> buildingContext(BuildingDetailResult building) {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "buildingId", building.id());
        put(result, "buildingName", building.buildingName());
        put(result, "buildingCode", building.buildingCode());
        put(result, "address", building.address());
        put(result, "structureType", building.structureType());
        put(result, "constructionYear", building.constructionYear());
        put(result, "floorCount", building.floorCount());
        put(result, "householdCount", building.householdCount());
        put(result, "residentCount", building.residentCount());
        put(result, "archiveCompletenessScore", building.archiveCompletenessScore());
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Dify 复核辅助输入序列化失败", ex);
        }
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static void copyMapValue(Map<?, ?> source, Map<String, Object> target, String key) {
        if (source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value instanceof UUID ? value.toString() : value);
        }
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record DifyToolResult(
            String workflowCode,
            String status,
            String summary,
            List<String> recommendations,
            List<String> warnings,
            long durationMs) {
    }
}
