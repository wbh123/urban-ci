package org.urbansafe.priority.ai.policy;

import java.util.Map;

/**
 * 人工智能结果进入第四阶段评分前的统一资格策略。
 *
 * <p>该策略只读取任务模式、状态、人工复核状态与模型业务启用门禁，不读取模型置信度。
 * 置信度描述模型检测输出，不能替代证据可靠性和房屋安全概率。
 */
public final class AiAssessmentEvidencePolicy {

    private AiAssessmentEvidencePolicy() {
    }

    public static Decision evaluate(Map<String, Object> task) {
        String status = upper(task.get("status"), "UNKNOWN");
        String mode = upper(task.get("mode"), "UNKNOWN");
        String reviewStatus = upper(task.get("reviewStatus"), "UNREVIEWED");
        String deploymentStage = upper(task.get("deploymentStage"), "VALIDATING");
        boolean formalEvidenceEnabled = booleanValue(task.get("formalEvidenceEnabled"));

        if (!"SUCCEEDED".equals(status)) {
            return new Decision(
                    "EXCLUDED",
                    false,
                    "NOT_USABLE",
                    "推理任务未成功完成，不能作为风险评分证据。");
        }
        if ("REJECTED".equals(reviewStatus)) {
            return new Decision(
                    "EXCLUDED",
                    false,
                    "HUMAN_REJECTED",
                    "人工复核已驳回该结果，不能作为风险评分证据。");
        }
        if ("MOCK".equals(mode)) {
            return new Decision(
                    "DEMO_ONLY",
                    false,
                    "SIMULATED",
                    "模拟推理仅用于业务链路演示，永远不能进入正式风险评分。");
        }
        if ("REAL".equals(mode)
                && ("CONFIRMED".equals(reviewStatus) || "CORRECTED".equals(reviewStatus))) {
            if ("ACTIVE".equals(deploymentStage) && formalEvidenceEnabled) {
                return new Decision(
                        "ELIGIBLE",
                        true,
                        "PROFESSIONAL_REVIEWED",
                        "真实模型结果已经人工确认或修正，且模型已启用正式证据，可按第四阶段规则作为辅助证据。");
            }
            return new Decision(
                    "DEMO_ONLY",
                    false,
                    "PROFESSIONAL_REVIEWED",
                    "结果已经人工复核，但模型仍处于验证、演示或影子运行阶段，不进入正式风险评分。");
        }
        if ("REAL".equals(mode)) {
            return new Decision(
                    "REVIEW_REQUIRED",
                    false,
                    "MODEL_UNREVIEWED",
                    "真实模型结果尚未完成人工复核，暂不进入正式风险评分。");
        }
        return new Decision(
                "EXCLUDED",
                false,
                "UNKNOWN_SOURCE",
                "推理模式无法识别，不能作为风险评分证据。");
    }

    private static String upper(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim().toUpperCase();
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public record Decision(
            String assessmentEligibility,
            boolean eligibleForFormalAssessment,
            String evidenceReliability,
            String assessmentNote) {
    }
}
