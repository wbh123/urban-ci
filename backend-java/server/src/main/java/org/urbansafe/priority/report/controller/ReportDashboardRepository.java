package org.urbansafe.priority.report.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

@Repository
public class ReportDashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public ReportDashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> dashboardRows(Scope scope) {
        MapSqlParameterSource params = scope.params();
        return jdbc.query("""
                SELECT b.id AS "buildingId", b.building_code AS "buildingCode",
                       b.building_name AS "buildingName", c.id AS "communityId",
                       c.community_name AS "communityName",
                       ST_X(g.centroid) AS "longitude", ST_Y(g.centroid) AS "latitude",
                       r.risk_score AS "riskScore", r.risk_level AS "riskLevel",
                       r.confidence_score AS "confidenceScore",
                       co.completeness_score AS "completenessScore",
                       p.priority_score AS "priorityScore", p.priority_level AS "priorityLevel",
                       p.ranking AS "ranking", COALESCE(r.need_manual_review, false) AS "needManualReview",
                       CASE WHEN r.id IS NULL THEN 'NO_RESULT'
                            WHEN r.status='STALE' OR co.status='STALE' OR p.status='STALE' THEN 'STALE'
                            ELSE 'CURRENT' END AS "freshness"
                FROM core.building b
                JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
                LEFT JOIN geo.building_geometry g ON g.building_id=b.id AND g.deleted_at IS NULL
                LEFT JOIN LATERAL (
                    SELECT x.* FROM core.risk_assessment x
                    WHERE x.building_id=b.id AND x.status IN ('CURRENT','STALE')
                    ORDER BY CASE x.status WHEN 'CURRENT' THEN 0 ELSE 1 END,
                             x.assessed_at DESC, x.id DESC
                    LIMIT 1
                ) r ON true
                LEFT JOIN LATERAL (
                    SELECT x.* FROM core.completeness_assessment x
                    WHERE x.building_id=b.id AND x.status IN ('CURRENT','STALE')
                    ORDER BY CASE x.status WHEN 'CURRENT' THEN 0 ELSE 1 END,
                             x.assessed_at DESC, x.id DESC
                    LIMIT 1
                ) co ON true
                LEFT JOIN LATERAL (
                    SELECT x.* FROM core.renewal_priority x
                    WHERE x.building_id=b.id AND x.ranking_scope_key='ALL'
                      AND x.status IN ('CURRENT','STALE')
                    ORDER BY CASE x.status WHEN 'CURRENT' THEN 0 ELSE 1 END,
                             x.generated_at DESC, x.id DESC
                    LIMIT 1
                ) p ON true
                WHERE b.deleted_at IS NULL
                  AND (:scopeType='ALL'
                    OR (:scopeType='REGION' AND c.administrative_region=:scopeId)
                    OR (:scopeType='COMMUNITY' AND b.community_id=:communityId))
                ORDER BY COALESCE(p.ranking, 2147483647), b.building_code, b.id
                """, params, rowMapper);
    }

    public Map<String, Object> building(UUID buildingId) {
        return one("""
                SELECT b.id AS "buildingId", b.building_code AS "buildingCode",
                       b.building_name AS "buildingName", b.address AS "address",
                       b.construction_year AS "constructionYear", b.structure_type AS "structureType",
                       b.floor_count AS "floorCount", b.building_area AS "buildingArea",
                       b.household_count AS "householdCount", b.resident_count AS "residentCount",
                       c.id AS "communityId", c.community_code AS "communityCode",
                       c.community_name AS "communityName",
                       c.administrative_region AS "administrativeRegion"
                FROM core.building b
                JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
                WHERE b.id=:buildingId AND b.deleted_at IS NULL
                """, Map.of("buildingId", buildingId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BUILDING_NOT_FOUND", "楼栋不存在"));
    }

    public List<Map<String, Object>> inspections(UUID buildingId) {
        return jdbc.query("""
                SELECT id AS "inspectionRecordId", inspected_at AS "inspectedAt",
                       inspection_part AS "inspectionPart", issue_type AS "issueType",
                       severity AS "severity", summary AS "summary",
                       rectification_suggestion AS "suggestion"
                FROM core.inspection_record
                WHERE building_id=:buildingId AND deleted_at IS NULL AND status='COMPLETED'
                ORDER BY inspected_at DESC NULLS LAST, id
                LIMIT 50
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public List<Map<String, Object>> evidence(UUID buildingId) {
        return jdbc.query("""
                SELECT id AS "evidenceId", evidence_type AS "evidenceType", title AS "title",
                       description AS "description", occurred_at AS "occurredAt",
                       reliability_level AS "reliabilityLevel", source AS "source"
                FROM core.building_evidence
                WHERE building_id=:buildingId AND deleted_at IS NULL
                ORDER BY occurred_at DESC NULLS LAST, id
                LIMIT 100
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public List<Map<String, Object>> aiEvidence(UUID buildingId) {
        return jdbc.query("""
                SELECT t.id AS "inferenceId", t.mode AS "mode", t.status AS "status",
                       t.review_status AS "reviewStatus", m.model_code AS "modelCode",
                       m.model_version AS "modelVersion", t.completed_at AS "completedAt",
                       m.deployment_stage AS "deploymentStage",
                       m.formal_evidence_enabled AS "formalEvidenceEnabled",
                       CASE WHEN t.mode='REAL' AND t.status='SUCCEEDED'
                                  AND t.review_status IN ('CONFIRMED','CORRECTED')
                                  AND m.deployment_stage='ACTIVE'
                                  AND m.formal_evidence_enabled=TRUE
                            THEN 'ELIGIBLE'
                            WHEN t.mode='MOCK' THEN 'DEMO_ONLY'
                            WHEN t.mode='REAL' AND t.status='SUCCEEDED'
                                  AND t.review_status IN ('CONFIRMED','CORRECTED')
                            THEN 'DEMO_ONLY'
                            WHEN t.mode='REAL' AND t.status='SUCCEEDED' THEN 'REVIEW_REQUIRED'
                            ELSE 'EXCLUDED' END AS "assessmentEligibility"
                FROM ai.inference_task t
                JOIN ai.model_registry m ON m.id=t.model_registry_id
                WHERE t.building_id=:buildingId
                ORDER BY t.created_at DESC, t.id
                LIMIT 100
                """, Map.of("buildingId", buildingId), rowMapper);
    }

    public void markStale(UUID buildingId, String templateVersion, String sourceChecksum) {
        jdbc.update("""
                UPDATE asset.generated_report
                SET report_status='STALE', updated_at=CURRENT_TIMESTAMP
                WHERE building_id=:buildingId AND template_version=:templateVersion
                  AND source_checksum<>:sourceChecksum AND report_status='GENERATED'
                  AND deleted_at IS NULL
                """, Map.of(
                "buildingId", buildingId,
                "templateVersion", templateVersion,
                "sourceChecksum", sourceChecksum));
    }

    public Optional<Map<String, Object>> findReusable(String idempotencyKey) {
        return one(reportSelect() + " WHERE gr.idempotency_key=:idempotencyKey"
                        + " AND gr.report_status='GENERATED' AND gr.deleted_at IS NULL",
                Map.of("idempotencyKey", idempotencyKey));
    }

    public void createGenerating(
            UUID reportId,
            String reportCode,
            UUID buildingId,
            UUID communityId,
            UUID riskAssessmentId,
            UUID renewalPriorityId,
            String sourceChecksum,
            String idempotencyKey,
            String snapshotJson,
            String summaryJson,
            UUID generatedBy,
            String riskRuleVersion,
            String renewalRuleVersion) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("reportCode", reportCode)
                .addValue("buildingId", buildingId)
                .addValue("communityId", communityId)
                .addValue("riskAssessmentId", riskAssessmentId)
                .addValue("renewalPriorityId", renewalPriorityId)
                .addValue("sourceChecksum", sourceChecksum)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("snapshotJson", snapshotJson)
                .addValue("summaryJson", summaryJson)
                .addValue("generatedBy", generatedBy)
                .addValue("riskRuleVersion", riskRuleVersion)
                .addValue("renewalRuleVersion", renewalRuleVersion);
        jdbc.update("""
                INSERT INTO asset.generated_report(
                    id, report_code, report_type, community_id, building_id,
                    risk_assessment_id, renewal_priority_id, report_status,
                    report_summary, data_version, risk_rule_version, renewal_rule_version,
                    template_version, report_format, source_checksum, idempotency_key,
                    report_snapshot, generated_by, created_at, updated_at)
                VALUES(
                    :reportId, :reportCode, 'BUILDING_RISK_REPORT', :communityId, :buildingId,
                    :riskAssessmentId, :renewalPriorityId, 'GENERATING',
                    CAST(:summaryJson AS jsonb), substring(:sourceChecksum from 1 for 16),
                    :riskRuleVersion, :renewalRuleVersion,
                    'phase5-report-v1', 'PDF', :sourceChecksum, :idempotencyKey,
                    CAST(:snapshotJson AS jsonb), :generatedBy,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, params);
    }

    public void complete(UUID reportId, StoredReport stored, long durationMs) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("reportId", reportId)
                .addValue("assetId", stored.assetId())
                .addValue("bucket", stored.bucket())
                .addValue("objectKey", stored.objectKey())
                .addValue("filename", stored.filename())
                .addValue("size", stored.bytes().length)
                .addValue("sha256", stored.sha256())
                .addValue("provider", stored.provider())
                .addValue("etag", stored.etag())
                .addValue("uploadedBy", stored.uploadedBy())
                .addValue("durationMs", durationMs);
        jdbc.update("""
                INSERT INTO asset.file_asset(
                    id, bucket_name, object_key, original_filename, content_type,
                    file_size, sha256, business_type, business_id, upload_status,
                    uploaded_by, metadata, storage_provider, object_etag)
                VALUES(
                    :assetId, :bucket, :objectKey, :filename, 'application/pdf',
                    :size, :sha256, 'GENERATED_REPORT', :reportId, 'AVAILABLE',
                    :uploadedBy, jsonb_build_object('reportId', :reportId),
                    :provider, :etag)
                """, params);
        jdbc.update("""
                UPDATE asset.generated_report
                SET file_asset_id=:assetId, report_status='GENERATED',
                    generation_duration_ms=:durationMs, generated_at=CURRENT_TIMESTAMP,
                    error_code=NULL, error_message=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE id=:reportId
                """, params);
    }

    public void fail(UUID reportId, String errorCode, String errorMessage) {
        jdbc.update("""
                UPDATE asset.generated_report
                SET report_status='FAILED', error_code=:errorCode,
                    error_message=:errorMessage, updated_at=CURRENT_TIMESTAMP
                WHERE id=:reportId
                """, Map.of(
                "reportId", reportId,
                "errorCode", errorCode,
                "errorMessage", errorMessage));
    }

    public Map<String, Object> report(UUID reportId) {
        return one(reportSelect() + " WHERE gr.id=:reportId AND gr.deleted_at IS NULL",
                Map.of("reportId", reportId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REPORT_NOT_FOUND", "报告不存在"));
    }

    public List<Map<String, Object>> list(
            UUID buildingId, UUID communityId, String status, int page, int size) {
        MapSqlParameterSource params = reportFilter(buildingId, communityId, status)
                .addValue("offset", page * size)
                .addValue("size", size);
        return jdbc.query(reportSelect() + reportWhere()
                        + " ORDER BY gr.created_at DESC, gr.id DESC OFFSET :offset LIMIT :size",
                params,
                rowMapper);
    }

    public long countReports(UUID buildingId, UUID communityId, String status) {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM asset.generated_report gr
                WHERE gr.deleted_at IS NULL
                  AND (CAST(:buildingId AS uuid) IS NULL
                       OR gr.building_id=CAST(:buildingId AS uuid))
                  AND (CAST(:communityId AS uuid) IS NULL
                       OR gr.community_id=CAST(:communityId AS uuid))
                  AND (CAST(:status AS varchar) IS NULL
                       OR gr.report_status=CAST(:status AS varchar))
                """, reportFilter(buildingId, communityId, status), Long.class);
        return value == null ? 0 : value;
    }

    private String reportSelect() {
        return """
                SELECT gr.id AS "reportId", gr.report_code AS "reportCode",
                       gr.building_id AS "buildingId", b.building_code AS "buildingCode",
                       b.building_name AS "buildingName", gr.community_id AS "communityId",
                       c.community_name AS "communityName",
                       gr.report_status AS "reportStatus",
                       gr.report_format AS "reportFormat",
                       gr.template_version AS "templateVersion",
                       gr.source_checksum AS "sourceChecksum",
                       gr.risk_rule_version AS "riskRuleVersion",
                       gr.renewal_rule_version AS "renewalRuleVersion",
                       gr.data_version AS "dataVersion",
                       gr.report_summary::text AS "reportSummaryJson",
                       gr.report_snapshot::text AS "reportSnapshotJson",
                       gr.generated_at AS "generatedAt", gr.created_at AS "createdAt",
                       gr.error_code AS "errorCode", gr.error_message AS "errorMessage",
                       fa.bucket_name AS "bucketName", fa.object_key AS "objectKey",
                       fa.original_filename AS "originalFilename",
                       fa.storage_provider AS "storageProvider",
                       rs.risk_level AS "riskLevel", rp.priority_level AS "priorityLevel"
                FROM asset.generated_report gr
                JOIN core.building b ON b.id=gr.building_id
                JOIN core.community c ON c.id=gr.community_id
                LEFT JOIN asset.file_asset fa
                       ON fa.id=gr.file_asset_id AND fa.deleted_at IS NULL
                LEFT JOIN core.risk_assessment rs ON rs.id=gr.risk_assessment_id
                LEFT JOIN core.renewal_priority rp ON rp.id=gr.renewal_priority_id
                """;
    }

    private String reportWhere() {
        return """
                WHERE gr.deleted_at IS NULL
                  AND (CAST(:buildingId AS uuid) IS NULL
                       OR gr.building_id=CAST(:buildingId AS uuid))
                  AND (CAST(:communityId AS uuid) IS NULL
                       OR gr.community_id=CAST(:communityId AS uuid))
                  AND (CAST(:status AS varchar) IS NULL
                       OR gr.report_status=CAST(:status AS varchar))
                """;
    }

    private MapSqlParameterSource reportFilter(
            UUID buildingId, UUID communityId, String status) {
        return new MapSqlParameterSource()
                .addValue("buildingId", buildingId)
                .addValue("communityId", communityId)
                .addValue("status", status);
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        return one(sql, new MapSqlParameterSource(params));
    }

    private Optional<Map<String, Object>> one(
            String sql, MapSqlParameterSource params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}

record Scope(String type, String id, UUID communityId) {

    static Scope parse(String scopeType, String scopeId) {
        String type = scopeType == null
                ? "ALL"
                : scopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ALL", "REGION", "COMMUNITY").contains(type)) {
            throw new org.urbansafe.priority.common.exception.InvalidRequestException(
                    "DASHBOARD_SCOPE_INVALID", "总览范围类型无效");
        }
        if ("ALL".equals(type)) {
            return new Scope("ALL", null, null);
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new org.urbansafe.priority.common.exception.InvalidRequestException(
                    "DASHBOARD_SCOPE_REQUIRED", "区域或社区范围必须提供 scopeId");
        }
        if ("COMMUNITY".equals(type)) {
            try {
                return new Scope(type, scopeId.trim(), UUID.fromString(scopeId.trim()));
            } catch (IllegalArgumentException ex) {
                throw new org.urbansafe.priority.common.exception.InvalidRequestException(
                        "DASHBOARD_SCOPE_INVALID", "社区范围标识必须为 UUID");
            }
        }
        return new Scope(type, scopeId.trim(), null);
    }

    String key() {
        return "ALL".equals(type) ? "ALL" : type + ":" + id;
    }

    MapSqlParameterSource params() {
        return new MapSqlParameterSource()
                .addValue("scopeType", type)
                .addValue("scopeId", id)
                .addValue("communityId", communityId);
    }
}
