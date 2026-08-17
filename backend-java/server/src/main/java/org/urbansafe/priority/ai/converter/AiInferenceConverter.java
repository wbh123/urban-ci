package org.urbansafe.priority.ai.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.urbansafe.priority.ai.policy.AiAssessmentEvidencePolicy;

/**
 * 推理结果响应转换器。
 *
 * <p>将持久层返回的受控 Map 转换为面向接口的响应结构，并补充免责声明和
 * 第四阶段评分证据资格。不依赖 OpenAPI DTO，也不直接访问持久层。
 */
public final class AiInferenceConverter {

    /** 风险筛查免责声明，必须在页面与结果中展示。 */
    public static final String DISCLAIMER =
            "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。"
                    + "对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。";

    private AiInferenceConverter() {
    }

    /** 转换推理任务详情为响应数据。 */
    public static Map<String, Object> toDetailResponse(Map<String, Object> task) {
        Map<String, Object> response = new LinkedHashMap<>(task);
        decorate(response);
        response.put("disclaimer", DISCLAIMER);
        return response;
    }

    /** 转换推理任务列表为分页响应。 */
    public static Map<String, Object> toListResponse(
            List<Map<String, Object>> tasks, long totalElements, int page, int size) {
        tasks.forEach(AiInferenceConverter::decorate);
        Map<String, Object> pageMeta = new LinkedHashMap<>();
        pageMeta.put("page", page);
        pageMeta.put("size", size);
        pageMeta.put("totalElements", totalElements);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        pageMeta.put("totalPages", totalPages);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", tasks);
        data.put("page", pageMeta);
        return data;
    }

    private static void decorate(Map<String, Object> item) {
        item.put("resultAvailable", "SUCCEEDED".equals(item.get("status")));
        item.put("detectionCount", detectionCount(item));
        item.put("detectionConsistency", detectionConsistency(item));
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(item);
        item.put("assessmentEligibility", decision.assessmentEligibility());
        item.put("eligibleForFormalAssessment", decision.eligibleForFormalAssessment());
        item.put("evidenceReliability", decision.evidenceReliability());
        item.put("assessmentNote", decision.assessmentNote());
        item.remove("version");
    }

    /**
     * 统一视觉检测数量口径。
     *
     * <p>详情响应中 structuredResult 保存的是模型成功返回时的原始结构化检测集合；ai.detection
     * 是它的持久化投影。历史版本曾因检测框校验口径不一致而出现“structuredResult 有框、投影表为 0”
     * 的脏数据，因此当详情同时携带 structuredResult 时，以其 detections 为展示与 Tool 的 canonical
     * 数量，并通过 detectionConsistency 显式暴露投影不一致。列表行通常不携带 structuredResult，
     * 此时继续使用 SQL 从 ai.detection 汇总的 detectionCount。
     */
    static int detectionCount(Map<String, Object> task) {
        Integer structuredCount = structuredDetectionCount(task);
        if (structuredCount != null) {
            return structuredCount;
        }
        Integer directCount = numericCount(task.get("detectionCount"));
        if (directCount != null) {
            return directCount;
        }
        Integer detailCount = listCount(task.get("detections"));
        if (detailCount != null) {
            return detailCount;
        }
        Object summary = task.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            Integer summaryCount = numericCount(summaryMap.get("detectionCount"));
            if (summaryCount != null) {
                return summaryCount;
            }
        }
        return 0;
    }

    /** 检测结构化结果、持久化投影和摘要计数是否一致；只比较当前响应实际携带的数据源。 */
    static String detectionConsistency(Map<String, Object> task) {
        Integer structuredCount = structuredDetectionCount(task);
        Integer directCount = numericCount(task.get("detectionCount"));
        Integer detailCount = listCount(task.get("detections"));
        Integer summaryCount = null;
        Object summary = task.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            summaryCount = numericCount(summaryMap.get("detectionCount"));
        }

        Integer reference = structuredCount != null
                ? structuredCount
                : directCount != null ? directCount : detailCount != null ? detailCount : summaryCount;
        if (reference == null) {
            return "CONSISTENT";
        }
        if ((directCount != null && !directCount.equals(reference))
                || (detailCount != null && !detailCount.equals(reference))
                || (summaryCount != null && !summaryCount.equals(reference))) {
            return "MISMATCH";
        }
        return "CONSISTENT";
    }

    private static Integer structuredDetectionCount(Map<String, Object> task) {
        Object structured = task.get("structuredResult");
        if (structured instanceof Map<?, ?> structuredMap
                && structuredMap.get("detections") instanceof List<?> structuredDetections) {
            return structuredDetections.size();
        }
        return null;
    }

    private static Integer listCount(Object value) {
        return value instanceof List<?> list ? list.size() : null;
    }

    private static Integer numericCount(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
