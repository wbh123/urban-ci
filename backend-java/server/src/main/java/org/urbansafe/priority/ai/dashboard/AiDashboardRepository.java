package org.urbansafe.priority.ai.dashboard;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * AI 态势大屏只读聚合仓储。
 *
 * <p>所有查询只读取现有业务事实表，不创建 AI 关注等级等重复事实，也不更新正式风险结果。
 */
@Repository
public class AiDashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AiDashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> metrics() {
        return jdbc.queryForMap("""
                SELECT
                  COUNT(DISTINCT CASE WHEN t.status='SUCCEEDED' THEN t.asset_id END) AS "aiAnalyzedImageCount",
                  COUNT(DISTINCT CASE WHEN t.status='SUCCEEDED' AND t.building_id IS NOT NULL THEN t.building_id END) AS "aiAnalyzedBuildingCount",
                  COUNT(*) FILTER (WHERE t.status='SUCCEEDED' AND t.review_status='UNREVIEWED') AS "pendingReviewCount",
                  (SELECT COUNT(*)
                     FROM ai.detection d
                     JOIN ai.inference_result ir ON ir.id=d.inference_result_id
                     JOIN ai.inference_task it ON it.id=ir.inference_task_id
                    WHERE it.status='SUCCEEDED') AS "detectionCount"
                FROM ai.inference_task t
                """, Map.of());
    }

    public Map<String, Object> todayMetrics() {
        return jdbc.queryForMap("""
                SELECT
                  COUNT(*) FILTER (
                    WHERE t.created_at >= CURRENT_DATE
                  ) AS "totalAnalyses",
                  COUNT(*) FILTER (
                    WHERE t.created_at >= CURRENT_DATE AND t.status='SUCCEEDED'
                  ) AS "succeeded",
                  COUNT(*) FILTER (
                    WHERE t.created_at >= CURRENT_DATE AND t.status IN ('PENDING','RUNNING')
                  ) AS "running",
                  COUNT(*) FILTER (
                    WHERE t.created_at >= CURRENT_DATE AND t.status='FAILED'
                  ) AS "failed",
                  (SELECT COUNT(*)
                     FROM ai.detection d
                     JOIN ai.inference_result ir ON ir.id=d.inference_result_id
                     JOIN ai.inference_task it ON it.id=ir.inference_task_id
                    WHERE it.created_at >= CURRENT_DATE AND it.status='SUCCEEDED'
                      AND d.class_code='CRACK') AS "crackCount",
                  (SELECT COUNT(*)
                     FROM ai.detection d
                     JOIN ai.inference_result ir ON ir.id=d.inference_result_id
                     JOIN ai.inference_task it ON it.id=ir.inference_task_id
                    WHERE it.created_at >= CURRENT_DATE AND it.status='SUCCEEDED'
                      AND d.class_code='SPALLING') AS "spallingCount",
                  (SELECT COUNT(*)
                     FROM ai.detection d
                     JOIN ai.inference_result ir ON ir.id=d.inference_result_id
                     JOIN ai.inference_task it ON it.id=ir.inference_task_id
                    WHERE it.created_at >= CURRENT_DATE AND it.status='SUCCEEDED'
                      AND d.class_code='WATER_STAIN') AS "waterStainCount",
                  (SELECT COUNT(*)
                     FROM ai.detection d
                     JOIN ai.inference_result ir ON ir.id=d.inference_result_id
                     JOIN ai.inference_task it ON it.id=ir.inference_task_id
                    WHERE it.created_at >= CURRENT_DATE AND it.status='SUCCEEDED'
                      AND d.class_code NOT IN ('CRACK','SPALLING','WATER_STAIN')) AS "otherDetectionCount"
                FROM ai.inference_task t
                """, Map.of());
    }

    public List<Map<String, Object>> buildingAiRows() {
        return jdbc.query("""
                SELECT b.id AS "buildingId",
                       COALESCE(ai_counts.visual_count, 0) AS "visualCount",
                       COALESCE(ai_counts.pending_review_count, 0) AS "pendingReviewCount",
                       COALESCE(inspections.inspection_count, 0) AS "inspectionCount",
                       COALESCE(archives.archive_count, 0) AS "archiveCount",
                       latest.latest_ai_at AS "latestAiAt",
                       CASE
                         WHEN latest.summary IS NULL THEN NULL
                         ELSE COALESCE(
                           latest.summary->>'summary',
                           trim(BOTH '"' FROM latest.summary::text)
                         )
                       END AS "latestAiSummary",
                       inspections.latest_inspection_at AS "latestInspectionAt"
                FROM core.building b
                LEFT JOIN LATERAL (
                  SELECT COUNT(*) FILTER (WHERE t.status='SUCCEEDED') AS visual_count,
                         COUNT(*) FILTER (
                           WHERE t.status='SUCCEEDED' AND t.review_status='UNREVIEWED'
                         ) AS pending_review_count
                    FROM ai.inference_task t
                   WHERE t.building_id=b.id
                ) ai_counts ON true
                LEFT JOIN LATERAL (
                  SELECT COALESCE(t.completed_at, t.created_at) AS latest_ai_at,
                         ir.summary AS summary
                    FROM ai.inference_task t
                    LEFT JOIN ai.inference_result ir ON ir.inference_task_id=t.id
                   WHERE t.building_id=b.id AND t.status='SUCCEEDED'
                   ORDER BY COALESCE(t.completed_at, t.created_at) DESC, t.id DESC
                   LIMIT 1
                ) latest ON true
                LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS inspection_count,
                         MAX(r.inspected_at) AS latest_inspection_at
                    FROM core.inspection_record r
                   WHERE r.building_id=b.id AND r.deleted_at IS NULL
                ) inspections ON true
                LEFT JOIN LATERAL (
                  SELECT COUNT(*) AS archive_count
                    FROM core.building_evidence e
                   WHERE e.building_id=b.id AND e.deleted_at IS NULL
                ) archives ON true
                WHERE b.deleted_at IS NULL
                ORDER BY b.building_code, b.id
                """, Map.of(), rowMapper);
    }

    public List<Map<String, Object>> latestFindings() {
        return jdbc.query("""
                WITH latest_task AS (
                  SELECT DISTINCT ON (t.building_id)
                         t.id, t.building_id
                    FROM ai.inference_task t
                   WHERE t.building_id IS NOT NULL AND t.status='SUCCEEDED'
                   ORDER BY t.building_id,
                            COALESCE(t.completed_at, t.created_at) DESC,
                            t.id DESC
                )
                SELECT lt.building_id AS "buildingId",
                       d.class_code AS "classCode",
                       d.class_name AS "className",
                       COUNT(*) AS "count",
                       MAX(d.confidence) AS "maxConfidence"
                  FROM latest_task lt
                  JOIN ai.inference_result ir ON ir.inference_task_id=lt.id
                  JOIN ai.detection d ON d.inference_result_id=ir.id
                 GROUP BY lt.building_id, d.class_code, d.class_name
                 ORDER BY lt.building_id, COUNT(*) DESC, d.class_code
                """, Map.of(), rowMapper);
    }

    public List<Map<String, Object>> activityRows(int limit) {
        return jdbc.query("""
                SELECT * FROM (
                  SELECT t.id::text AS "eventId",
                         'AI_ANALYSIS' AS "eventType",
                         COALESCE(t.completed_at, t.updated_at, t.created_at) AS "occurredAt",
                         t.status AS "status",
                         t.building_id AS "buildingId",
                         b.building_name AS "buildingName",
                         c.community_name AS "communityName",
                         COALESCE((
                           SELECT COUNT(*)
                             FROM ai.inference_result ir
                             JOIN ai.detection d ON d.inference_result_id=ir.id
                            WHERE ir.inference_task_id=t.id
                         ), 0) AS "detectionCount"
                    FROM ai.inference_task t
                    LEFT JOIN core.building b ON b.id=t.building_id AND b.deleted_at IS NULL
                    LEFT JOIN core.community c ON c.id=t.community_id AND c.deleted_at IS NULL

                  UNION ALL

                  SELECT r.id::text AS "eventId",
                         'AI_REVIEW' AS "eventType",
                         r.reviewed_at AS "occurredAt",
                         r.review_status AS "status",
                         t.building_id AS "buildingId",
                         b.building_name AS "buildingName",
                         c.community_name AS "communityName",
                         0 AS "detectionCount"
                    FROM ai.inference_review r
                    JOIN ai.inference_task t ON t.id=r.inference_task_id
                    LEFT JOIN core.building b ON b.id=t.building_id AND b.deleted_at IS NULL
                    LEFT JOIN core.community c ON c.id=t.community_id AND c.deleted_at IS NULL
                ) events
                WHERE events."occurredAt" IS NOT NULL
                ORDER BY events."occurredAt" DESC, events."eventId" DESC
                LIMIT :limit
                """, Map.of("limit", limit), rowMapper);
    }
}
