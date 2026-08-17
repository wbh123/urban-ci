package org.urbansafe.priority.ai.review;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.ai.command.ReviewCommand;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.assessment.service.AssessmentInvalidationService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/**
 * 人工复核应用服务。
 *
 * <p>复核风险度仅作为人工辅助修正保存到 inference_review.corrected_data，
 * 不写入正式风险评分、更新优先级或鉴定结论。复核改变可用于正式评分的 AI 证据集合后，
 * 旧 CURRENT 评分会被标记为 STALE，必须由人工显式重新评估生成新版本。
 */
@Service
public class AiReviewApplicationService {

    private static final java.util.Set<String> REVIEWED_RISK_LEVELS =
            java.util.Set.of("LOW", "MEDIUM", "HIGH", "VERY_HIGH");

    private final AiInferenceService inferenceService;
    private final AiReviewCorrectionRepository correctionRepository;
    private final AssessmentInvalidationService assessmentInvalidationService;

    public AiReviewApplicationService(
            AiInferenceService inferenceService,
            AiReviewCorrectionRepository correctionRepository,
            AssessmentInvalidationService assessmentInvalidationService) {
        this.inferenceService = inferenceService;
        this.correctionRepository = correctionRepository;
        this.assessmentInvalidationService = assessmentInvalidationService;
    }

    @Transactional
    public Map<String, Object> review(ReviewCommand command) {
        Map<String, Object> correctedData = normalizeCorrectedData(command.correctedData());
        Map<String, Object> result = new LinkedHashMap<>(inferenceService.review(command));
        if (!correctedData.isEmpty()) {
            int updated = correctionRepository.updateLatest(
                    command.inferenceId(), command.reviewedBy(), correctedData);
            if (updated != 1) {
                throw new ResourceConflictException(
                        "AI_REVIEW_CORRECTION_CONFLICT",
                        "人工复核已创建，但结构化风险修正未能绑定到本次复核记录");
            }
        }
        boolean refreshRequired = assessmentInvalidationService.invalidateAfterAiReview(command.inferenceId());
        result.put("correctedData", correctedData);
        result.put("assessmentRefreshRequired", refreshRequired);
        return result;
    }

    public Map<String, Object> enrichDetail(Map<String, Object> source, java.util.UUID inferenceId) {
        Map<String, Object> detail = new LinkedHashMap<>(source);
        correctionRepository.latest(inferenceId).ifPresent(correctedData -> {
            Object rawLatestReview = detail.get("latestReview");
            Map<String, Object> latestReview = new LinkedHashMap<>();
            if (rawLatestReview instanceof Map<?, ?> raw) {
                raw.forEach((key, value) -> {
                    if (key != null) latestReview.put(String.valueOf(key), value);
                });
            }
            latestReview.put("correctedData", correctedData);
            detail.put("latestReview", latestReview);
        });
        return detail;
    }

    static Map<String, Object> normalizeCorrectedData(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Object rawLevel = source.get("reviewedRiskLevel");
        if (rawLevel == null || String.valueOf(rawLevel).isBlank()) return Map.of();
        String level = String.valueOf(rawLevel).trim().toUpperCase(Locale.ROOT);
        if (!REVIEWED_RISK_LEVELS.contains(level)) {
            throw new InvalidRequestException(
                    "AI_REVIEW_RISK_LEVEL_INVALID",
                    "人工复核风险度无效，仅支持 LOW、MEDIUM、HIGH、VERY_HIGH");
        }
        return Map.of("reviewedRiskLevel", level);
    }
}
