package org.urbansafe.priority.assessment.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 当人工复核改变正式评分可用的 REAL AI 证据集合时，使依赖旧输入的正式评分退出 CURRENT。
 *
 * <p>MOCK 结果从不进入正式评分，因此复核 MOCK 不会影响正式结果新鲜度。
 * 这里只改变结果新鲜度，不计算新分数；新的 CURRENT 结果必须由显式重新评估生成。
 */
@Service
public class AssessmentInvalidationService {

    static final String AI_REVIEW_CHANGED_REASON = "AI_REVIEW_CHANGED";

    private final NamedParameterJdbcTemplate jdbc;

    public AssessmentInvalidationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public boolean invalidateAfterAiReview(UUID inferenceId) {
        UUID buildingId = findFormalEvidenceBuildingId(inferenceId);
        if (buildingId == null) {
            return false;
        }
        Map<String, Object> params = Map.of(
                "buildingId", buildingId,
                "reason", AI_REVIEW_CHANGED_REASON);
        int completeness = jdbc.update("""
                UPDATE core.completeness_assessment
                SET status='STALE', stale_reason=:reason
                WHERE building_id=:buildingId AND status='CURRENT'
                """, params);
        int risk = jdbc.update("""
                UPDATE core.risk_assessment
                SET status='STALE', stale_reason=:reason, updated_at=CURRENT_TIMESTAMP
                WHERE building_id=:buildingId AND status='CURRENT'
                """, params);
        int renewal = jdbc.update("""
                UPDATE core.renewal_priority
                SET status='STALE', stale_reason=:reason
                WHERE building_id=:buildingId AND status='CURRENT'
                """, params);
        return completeness + risk + renewal > 0;
    }

    private UUID findFormalEvidenceBuildingId(UUID inferenceId) {
        try {
            return jdbc.queryForObject("""
                    SELECT building_id
                    FROM ai.inference_task
                    WHERE id=:inferenceId
                      AND building_id IS NOT NULL
                      AND mode='REAL'
                    """, Map.of("inferenceId", inferenceId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
