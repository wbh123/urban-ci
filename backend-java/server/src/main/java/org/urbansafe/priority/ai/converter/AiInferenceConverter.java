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
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(item);
        item.put("assessmentEligibility", decision.assessmentEligibility());
        item.put("eligibleForFormalAssessment", decision.eligibleForFormalAssessment());
        item.put("evidenceReliability", decision.evidenceReliability());
        item.put("assessmentNote", decision.assessmentNote());
        item.remove("version");
    }

    @SuppressWarnings("unchecked")
    private static int detectionCount(Map<String, Object> task) {
        Object detections = task.get("detections");
        if (detections instanceof List<?> list) {
            return list.size();
        }
        Object summary = task.get("summary");
        if (summary instanceof Map<?, ?> summaryMap && summaryMap.get("detectionCount") instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
