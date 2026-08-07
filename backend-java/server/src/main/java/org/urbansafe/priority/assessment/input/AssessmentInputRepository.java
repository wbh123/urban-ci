package org.urbansafe.priority.assessment.input;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 第四阶段统一输入仓储。三个计算器不得自行查询数据库。
 *
 * <p>所有查询统一执行逻辑删除、业务状态、图片可用性、反馈窗口和人工智能资格筛选。
 */
@Repository
public class AssessmentInputRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AssessmentInputRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Map<String, Object>> findBuilding(UUID buildingId) {
        return one("""
                SELECT b.id AS "buildingId", b.community_id AS "communityId",
                       b.building_code AS "buildingCode", b.building_name AS "buildingName",
                       b.address AS "address", b.construction_year AS "constructionYear",
                       b.structure_type AS "structureType", b.floor_count AS "floorCount",
                       b.building_area AS "buildingArea", b.household_count AS "householdCount",
                       b.resident_count AS "residentCount", b.elderly_count AS "elderlyCount",
                       b.child_count AS "childCount",
                       b.has_illegal_modification AS "illegalModification",
                       b.has_ground_floor_business AS "groundFloorBusiness",
                       b.extra_attributes AS "extraAttributes",
                       c.community_code AS "communityCode", c.community_name AS "communityName",
                       c.administrative_region AS "administrativeRegion"
                FROM core.building b
                JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
                WHERE b.id=:buildingId AND b.deleted_at IS NULL
                """, Map.of("buildingId", buildingId));
    }

    public boolean hasGeometry(UUID buildingId) {
        Boolean result = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM geo.building_geometry
                    WHERE building_id=:buildingId AND deleted_at IS NULL
                )
                """, Map.of("buildingId", buildingId), Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    public List<Map<String, Object>> findCompletedInspections(UUID buildingId) {
        return jdbc.query("""
                SELECT r.id AS "inspectionRecordId", r.inspected_at AS "inspectedAt",
                       r.inspection_part AS "inspectionPart", r.form_data AS "formData"
                FROM core.inspection_record r
                LEFT JOIN core.inspection_task t
                  ON t.id=r.inspection_task_id AND t.deleted_at IS NULL
                WHERE r.building_id=:buildingId
                  AND r.deleted_at IS NULL
                  AND r.status='COMPLETED'
                  AND (r.inspection_task_id IS NULL OR t.status='COMPLETED')
                ORDER BY r.inspected_at, r.id
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public Map<String, Object> findAvailableInspectionImages(UUID buildingId) {
        try {
            return jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT a.id) AS "imageCount",
                           COALESCE(jsonb_agg(DISTINCT r.inspection_part)
                              FILTER (WHERE r.inspection_part IS NOT NULL), '[]'::jsonb) AS "parts"
                    FROM core.inspection_record r
                    JOIN asset.asset_binding ab
                      ON ab.business_type='INSPECTION_RECORD'
                     AND ab.business_id=r.id
                     AND ab.deleted_at IS NULL
                    JOIN asset.file_asset a
                      ON a.id=ab.asset_id
                     AND a.deleted_at IS NULL
                     AND a.upload_status='AVAILABLE'
                    WHERE r.building_id=:buildingId
                      AND r.deleted_at IS NULL
                      AND r.status='COMPLETED'
                    """, Map.of("buildingId", buildingId), rowMapper);
        } catch (EmptyResultDataAccessException ex) {
            return Map.of("imageCount", 0, "parts", List.of());
        }
    }

    public List<Map<String, Object>> findBusinessEvidence(UUID buildingId) {
        return jdbc.query("""
                SELECT id AS "evidenceId", evidence_type AS "evidenceType",
                       reliability_level AS "reliabilityLevel", occurred_at AS "occurredAt",
                       evidence_data AS "evidenceData"
                FROM core.building_evidence
                WHERE building_id=:buildingId AND deleted_at IS NULL
                ORDER BY evidence_type, occurred_at, id
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public List<Map<String, Object>> findResidentReports(UUID buildingId, LocalDate calculationDate) {
        return jdbc.query("""
                SELECT id AS "reportId", report_type AS "reportType",
                       urgency AS "urgency", status AS "status", submitted_at AS "submittedAt"
                FROM core.resident_report
                WHERE building_id=:buildingId
                  AND deleted_at IS NULL
                  AND status NOT IN ('REJECTED','CANCELLED')
                  AND submitted_at >= CAST(:windowStart AS date)
                  AND submitted_at < CAST(:windowEnd AS date) + INTERVAL '1 day'
                ORDER BY submitted_at, id
                """, Map.of(
                        "buildingId", buildingId,
                        "windowStart", calculationDate.minusDays(365),
                        "windowEnd", calculationDate), rowMapper);
    }

    public List<Map<String, Object>> findEligibleAiEvidence(UUID buildingId) {
        return jdbc.query("""
                WITH latest_review AS (
                    SELECT DISTINCT ON (inference_task_id)
                           inference_task_id, corrected_data
                    FROM ai.inference_review
                    WHERE corrected_data IS NOT NULL
                    ORDER BY inference_task_id, reviewed_at DESC, id DESC
                ), eligible_task AS (
                    SELECT t.id AS inference_id, t.mode, t.status, t.review_status,
                           r.id AS result_id, r.summary, lr.corrected_data
                    FROM ai.inference_task t
                    JOIN ai.inference_result r ON r.inference_task_id=t.id
                    LEFT JOIN latest_review lr ON lr.inference_task_id=t.id
                    WHERE t.building_id=:buildingId
                      AND t.mode='REAL'
                      AND t.status='SUCCEEDED'
                      AND t.review_status IN ('CONFIRMED','CORRECTED')
                ), expanded AS (
                    SELECT e.inference_id, e.mode, e.status, e.review_status,
                           COALESCE(defect->>'defectType', defect->>'classCode', defect->>'className') AS defect_type,
                           COALESCE(defect->>'severity', defect->>'riskSeverity') AS severity,
                           CASE WHEN COALESCE(defect->>'quantity','') ~ '^[0-9]+$'
                                THEN (defect->>'quantity')::integer ELSE 1 END AS quantity,
                           COALESCE(defect->>'part', defect->>'inspectionPart') AS part
                    FROM eligible_task e
                    CROSS JOIN LATERAL jsonb_array_elements(
                        CASE
                          WHEN e.review_status='CORRECTED'
                           AND jsonb_typeof(e.corrected_data->'defects')='array'
                            THEN e.corrected_data->'defects'
                          WHEN e.review_status='CORRECTED'
                           AND e.corrected_data IS NOT NULL
                            THEN jsonb_build_array(e.corrected_data)
                          ELSE '[]'::jsonb
                        END
                    ) defect
                    WHERE e.review_status='CORRECTED'
                    UNION ALL
                    SELECT e.inference_id, e.mode, e.status, e.review_status,
                           COALESCE(d.class_code, d.class_name) AS defect_type,
                           d.extra_data->>'severity' AS severity,
                           1 AS quantity,
                           COALESCE(d.extra_data->>'part', e.summary->>'part') AS part
                    FROM eligible_task e
                    JOIN ai.detection d ON d.inference_result_id=e.result_id
                    WHERE e.review_status='CONFIRMED'
                )
                SELECT inference_id AS "inferenceId", mode AS "mode", status AS "status",
                       review_status AS "reviewStatus",
                       'ELIGIBLE' AS "assessmentEligibility",
                       COALESCE(NULLIF(defect_type, ''), 'UNKNOWN') AS "defectType",
                       COALESCE(NULLIF(severity, ''), 'NONE') AS "severity",
                       SUM(GREATEST(quantity, 1))::integer AS "quantity",
                       NULLIF(part, '') AS "part"
                FROM expanded
                GROUP BY inference_id, mode, status, review_status, defect_type, severity, part
                ORDER BY inference_id, defect_type, severity, part
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public List<Map<String, Object>> findExcludedAiEvidence(UUID buildingId) {
        return jdbc.query("""
                SELECT t.id AS "inferenceId", t.mode AS "mode", t.status AS "status",
                       t.review_status AS "reviewStatus",
                       CASE
                         WHEN t.status <> 'SUCCEEDED' THEN 'EXCLUDED'
                         WHEN t.review_status='REJECTED' THEN 'EXCLUDED'
                         WHEN t.mode='MOCK' THEN 'DEMO_ONLY'
                         WHEN t.mode='REAL' THEN 'REVIEW_REQUIRED'
                         ELSE 'EXCLUDED'
                       END AS "assessmentEligibility",
                       CASE
                         WHEN t.status <> 'SUCCEEDED' THEN '推理任务未成功完成'
                         WHEN t.review_status='REJECTED' THEN '人工复核已驳回'
                         WHEN t.mode='MOCK' THEN '模拟结果仅用于演示'
                         WHEN t.mode='REAL' THEN '真实结果尚未完成人工确认或修正'
                         ELSE '推理模式或证据来源不可用'
                       END AS "exclusionReason"
                FROM ai.inference_task t
                WHERE t.building_id=:buildingId
                  AND NOT (
                    t.mode='REAL'
                    AND t.status='SUCCEEDED'
                    AND t.review_status IN ('CONFIRMED','CORRECTED')
                  )
                ORDER BY t.created_at, t.id
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public List<Map<String, Object>> findSpatialMetrics(UUID buildingId, LocalDate calculationDate) {
        return jdbc.query("""
                SELECT metric_code AS "metricCode", metric_value AS "metricValue",
                       metric_text AS "metricText", calculated_at AS "calculatedAt",
                       expires_at AS "expiresAt"
                FROM geo.spatial_metric
                WHERE building_id=:buildingId
                  AND (expires_at IS NULL OR expires_at >= CAST(:calculationDate AS date))
                ORDER BY metric_code, calculated_at, id
                """, Map.of(
                        "buildingId", buildingId,
                        "calculationDate", calculationDate), rowMapper);
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
