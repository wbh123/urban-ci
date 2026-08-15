package org.urbansafe.priority.inspection.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/**
 * 巡检员现场记录 + 已有 AI 视觉识别结果的综合摘要。
 *
 * <p>该服务不会触发新的视觉推理。视觉事实只读取既有 inference 结果；文本智能能力
 * 不可用时，基于同一批真实数据生成确定性摘要。
 */
@Service
public class InspectionAiSummaryService {

    private static final String DISCLAIMER =
            "综合总结仅用于巡检辅助，不构成专业鉴定结论；疑似病害仍需结合原图和现场情况人工复核。";

    private final Phase2InspectionService inspectionService;
    private final AiInferenceService inferenceService;
    private final SpringAiOrchestrationService orchestrationService;

    public InspectionAiSummaryService(
            Phase2InspectionService inspectionService,
            AiInferenceService inferenceService,
            SpringAiOrchestrationService orchestrationService) {
        this.inspectionService = inspectionService;
        this.inferenceService = inferenceService;
        this.orchestrationService = orchestrationService;
    }

    public Map<String, Object> summarize(
            UUID taskId,
            UUID inferenceId,
            UUID requestedBy,
            String requestedByName) {
        Map<String, Object> task = inspectionService.getTask(taskId);
        List<Map<String, Object>> records = inspectionService.listRecords(taskId);
        Map<String, Object> inference = inferenceService.getDetail(inferenceId);
        validateRelation(taskId, inference);

        UUID buildingId = uuid(task.get("buildingId"));
        if (buildingId == null) buildingId = uuid(inference.get("buildingId"));
        DeterministicSummary fallback = deterministic(records, inference);

        try {
            SpringAiOrchestrationService.IntelligentAnalysisResult ai =
                    orchestrationService.runIntelligentAnalysis(
                            "INSPECTION_SUMMARY",
                            buildingId,
                            buildQuestion(task, records, inference),
                            buildingId == null ? Map.of() : Map.of("buildingId", buildingId.toString()),
                            requestedBy,
                            requestedByName);
            if (ai.modelCode() == null
                    || ai.answer() == null
                    || ai.answer().isBlank()
                    || "FAILED".equals(ai.status().name())) {
                return response("RULE_FALLBACK", fallback, null);
            }
            DeterministicSummary combined = new DeterministicSummary(
                    labeled(ai.answer(), "现场描述", fallback.fieldDescription()),
                    labeled(ai.answer(), "AI视觉发现", fallback.visualFindings()),
                    labeled(ai.answer(), "相互印证或冲突", fallback.agreement()),
                    labeled(ai.answer(), "重点位置", fallback.keyLocations()),
                    labeled(ai.answer(), "建议补充证据", fallback.evidenceGaps()),
                    labeled(ai.answer(), "人工复核建议", fallback.reviewSuggestion()));
            return response("AI", combined, ai);
        } catch (RuntimeException ex) {
            return response("RULE_FALLBACK", fallback, null);
        }
    }

    private static void validateRelation(UUID taskId, Map<String, Object> inference) {
        UUID inferenceTaskId = uuid(inference.get("inspectionTaskId"));
        if (inferenceTaskId != null && !taskId.equals(inferenceTaskId)) {
            throw new ResourceConflictException(
                    "INSPECTION_AI_CONTEXT_MISMATCH", "所选 AI 识别结果不属于当前巡检任务");
        }
        String status = text(inference.get("status"));
        if (status != null && !"SUCCEEDED".equalsIgnoreCase(status)) {
            throw new ResourceConflictException(
                    "INSPECTION_AI_RESULT_NOT_READY", "请选择已完成的 AI 视觉识别结果生成综合总结");
        }
    }

    private static String buildQuestion(
            Map<String, Object> task,
            List<Map<String, Object>> records,
            Map<String, Object> inference) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请只基于以下已经存在的巡检员记录与 AI 视觉识别结果生成巡检综合总结。\n")
                .append("禁止重新调用视觉模型，禁止编造未提供的现场事实。\n")
                .append("任务：").append(textOr(task.get("title"), "现场巡检")).append("\n")
                .append("巡检员记录：\n");
        for (Map<String, Object> record : records) {
            prompt.append("- 部位=").append(textOr(record.get("inspectionPart"), "现场检查"))
                    .append("；严重程度=").append(textOr(record.get("severity"), "未标注"))
                    .append("；描述=").append(textOr(record.get("summary"), "未填写"));
            String suggestion = text(record.get("rectificationSuggestion"));
            if (suggestion != null) prompt.append("；现场建议=").append(suggestion);
            prompt.append("\n");
        }
        prompt.append("已有 AI 视觉发现：\n");
        for (String detection : detectionDescriptions(inference)) {
            prompt.append("- ").append(detection).append("\n");
        }
        prompt.append("请严格按六行输出：\n")
                .append("现场描述：...\n")
                .append("AI视觉发现：...\n")
                .append("相互印证或冲突：...\n")
                .append("重点位置：...\n")
                .append("建议补充证据：...\n")
                .append("人工复核建议：...\n")
                .append("低于40%可信度的视觉候选不得输出具体百分比。AI 不得替代人工专业结论。\n");
        return prompt.toString();
    }

    private static DeterministicSummary deterministic(
            List<Map<String, Object>> records,
            Map<String, Object> inference) {
        List<String> fieldItems = new ArrayList<>();
        Set<String> locations = new LinkedHashSet<>();
        boolean highSeverity = false;
        for (Map<String, Object> record : records) {
            String part = textOr(record.get("inspectionPart"), "现场检查");
            String summary = textOr(record.get("summary"), "未填写具体描述");
            fieldItems.add(part + "：" + summary);
            locations.add(part);
            highSeverity |= "HIGH".equalsIgnoreCase(text(record.get("severity")));
        }
        String fieldDescription = fieldItems.isEmpty()
                ? "当前没有可用的巡检员文字记录。"
                : String.join("；", fieldItems);

        List<String> detections = detectionDescriptions(inference);
        String visualFindings = detections.isEmpty()
                ? "当前所选图片未形成可展示的疑似病害候选。"
                : String.join("；", detections);

        String fieldLower = fieldDescription.toLowerCase(Locale.ROOT);
        List<String> matched = detectionNames(inference).stream()
                .filter(name -> fieldLower.contains(name.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        String agreement = matched.isEmpty()
                ? "巡检员文字与视觉候选的关注点需由人工逐项对照，当前不自动判定两者一致。"
                : "巡检员描述与视觉识别在“" + String.join("、", matched) + "”方面存在相互印证。";
        String keyLocations = locations.isEmpty() ? "具体位置需人工补充。" : String.join("、", locations);

        Set<String> suggestions = new LinkedHashSet<>();
        for (Map<String, Object> record : records) {
            String suggestion = text(record.get("rectificationSuggestion"));
            if (suggestion != null) suggestions.add(suggestion);
        }
        String evidenceGaps = suggestions.isEmpty()
                ? "建议补充病害近距离照片、尺度参照和周边整体照片，便于专业复核。"
                : String.join("；", suggestions) + "；必要时补充带尺度参照的近距离照片。";
        String reviewSuggestion = highSeverity
                ? "巡检员已标记较高严重程度，建议优先进行人工专业复核，并核对原图标注区域。"
                : detections.isEmpty()
                        ? "建议结合现场记录确认是否需要补拍或进一步检查。"
                        : "存在 AI 疑似病害候选，建议人工核对原图、位置与现场描述后确认或修正。";
        return new DeterministicSummary(
                fieldDescription, visualFindings, agreement, keyLocations, evidenceGaps, reviewSuggestion);
    }

    private static Map<String, Object> response(
            String mode,
            DeterministicSummary summary,
            SpringAiOrchestrationService.IntelligentAnalysisResult ai) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("fieldDescription", summary.fieldDescription());
        result.put("visualFindings", summary.visualFindings());
        result.put("agreement", summary.agreement());
        result.put("keyLocations", summary.keyLocations());
        result.put("evidenceGaps", summary.evidenceGaps());
        result.put("reviewSuggestion", summary.reviewSuggestion());
        result.put("durationMs", ai == null ? 0L : ai.durationMs());
        result.put("modelCode", ai == null ? null : ai.modelCode());
        result.put("disclaimer", DISCLAIMER);
        return result;
    }

    private static List<String> detectionDescriptions(Map<String, Object> inference) {
        List<String> result = new ArrayList<>();
        Object raw = inference.get("detections");
        if (!(raw instanceof List<?> list)) return result;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> detection)) continue;
            String name = textOr(detection.get("className"), textOr(detection.get("classCode"), "疑似病害"));
            Object confidenceRaw = detection.get("confidence");
            if (confidenceRaw instanceof Number confidence) {
                double value = confidence.doubleValue();
                if (value >= 0.4d) {
                    result.add(name + "（" + Math.round(value * 100d) + "%）");
                } else {
                    result.add(name + "（低可信，需人工确认）");
                }
            } else {
                result.add(name);
            }
        }
        return result;
    }

    private static List<String> detectionNames(Map<String, Object> inference) {
        List<String> result = new ArrayList<>();
        Object raw = inference.get("detections");
        if (!(raw instanceof List<?> list)) return result;
        for (Object item : list) {
            if (item instanceof Map<?, ?> detection) {
                String name = text(detection.get("className"));
                if (name != null) result.add(name);
            }
        }
        return result;
    }

    private static String labeled(String answer, String label, String fallback) {
        for (String rawLine : answer.split("\\R")) {
            String line = rawLine.strip().replaceFirst("^\\d+[.、)]\\s*", "");
            if (!line.startsWith(label)) continue;
            int cn = line.indexOf('：');
            int en = line.indexOf(':');
            int index = cn >= 0 ? cn : en;
            if (index >= 0 && index + 1 < line.length()) {
                String value = line.substring(index + 1).strip();
                if (!value.isBlank()) return value;
            }
        }
        return fallback;
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return String.valueOf(value).trim();
    }

    private static String textOr(Object value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    private record DeterministicSummary(
            String fieldDescription,
            String visualFindings,
            String agreement,
            String keyLocations,
            String evidenceGaps,
            String reviewSuggestion) {
    }
}
