package org.urbansafe.priority.assessment.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.assessment.model.AssessmentResults.CompletenessResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RenewalResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

/** 第四阶段评分结果、历史和排行榜持久层。 */
@Repository
public class AssessmentResultRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AssessmentResultRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void lockBuilding(UUID buildingId) {
        try {
            jdbc.queryForObject(
                    "SELECT id FROM core.building WHERE id=:id AND deleted_at IS NULL FOR UPDATE",
                    Map.of("id", buildingId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            throw new org.urbansafe.priority.common.exception.ResourceNotFoundException(
                    "BUILDING_NOT_FOUND", "楼栋不存在");
        }
    }

    public Optional<Map<String, Object>> reusableCompleteness(
            UUID buildingId, String checksum, UUID ruleId, String engineVersion) {
        return one("""
                SELECT id AS "assessmentId", input_checksum AS "inputChecksum",
                       rule_version_id AS "ruleVersionId", engine_version AS "engineVersion",
                       status AS "status"
                FROM core.completeness_assessment
                WHERE building_id=:buildingId AND input_checksum=:checksum
                  AND rule_version_id=:ruleId AND engine_version=:engineVersion
                  AND status='CURRENT'
                ORDER BY assessed_at DESC LIMIT 1
                """, Map.of("buildingId", buildingId, "checksum", checksum,
                "ruleId", ruleId, "engineVersion", engineVersion));
    }

    public Optional<Map<String, Object>> reusableRisk(
            UUID buildingId, String checksum, UUID ruleId, String engineVersion) {
        return one("""
                SELECT r.id AS "assessmentId", r.input_checksum AS "inputChecksum",
                       r.rule_version_id AS "ruleVersionId", r.engine_version AS "engineVersion",
                       r.status AS "status"
                FROM core.risk_assessment r
                JOIN core.completeness_assessment ca
                  ON ca.id=r.completeness_assessment_id AND ca.status='CURRENT'
                WHERE r.building_id=:buildingId AND r.input_checksum=:checksum
                  AND r.rule_version_id=:ruleId AND r.engine_version=:engineVersion
                  AND r.status='CURRENT'
                ORDER BY r.assessed_at DESC LIMIT 1
                """, Map.of("buildingId", buildingId, "checksum", checksum,
                "ruleId", ruleId, "engineVersion", engineVersion));
    }

    public Optional<Map<String, Object>> reusableRenewal(
            UUID buildingId, String scopeKey, String checksum, UUID ruleId, String engineVersion) {
        return one("""
                SELECT p.id AS "priorityId", p.input_checksum AS "inputChecksum",
                       p.rule_version_id AS "ruleVersionId", p.engine_version AS "engineVersion",
                       p.status AS "status"
                FROM core.renewal_priority p
                JOIN core.risk_assessment r ON r.id=p.risk_assessment_id AND r.status='CURRENT'
                JOIN core.completeness_assessment ca
                  ON ca.id=r.completeness_assessment_id AND ca.status='CURRENT'
                WHERE p.building_id=:buildingId AND p.ranking_scope_key=:scopeKey
                  AND p.input_checksum=:checksum AND p.rule_version_id=:ruleId
                  AND p.engine_version=:engineVersion AND p.status='CURRENT'
                ORDER BY p.generated_at DESC LIMIT 1
                """, Map.of("buildingId", buildingId, "scopeKey", scopeKey,
                "checksum", checksum, "ruleId", ruleId, "engineVersion", engineVersion));
    }

    @Transactional
    public UUID saveCompleteness(
            UUID buildingId, UUID batchId, RuleSnapshot rule, String engineVersion,
            String triggerType, UUID triggeredBy, JsonNode snapshot, String checksum,
            CompletenessResult result) {
        jdbc.update("""
                UPDATE core.completeness_assessment
                SET status='SUPERSEDED', stale_reason='NEW_CURRENT_RESULT'
                WHERE building_id=:buildingId AND status='CURRENT'
                """, Map.of("buildingId", buildingId));
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO core.completeness_assessment
                  (id, building_id, assessment_version, rule_version_id,
                   completeness_score, completeness_level, available_items, missing_items,
                   suggestions, dimension_scores, input_snapshot, input_checksum, status,
                   assessed_at, created_at, calculation_batch_id, engine_version,
                   trigger_type, triggered_by)
                VALUES
                  (:id,:buildingId,:version,:ruleId,:score,:level,CAST(:available AS jsonb),
                   CAST(:missing AS jsonb),CAST(:suggestions AS jsonb),CAST(:dimensions AS jsonb),
                   CAST(:snapshot AS jsonb),:checksum,'CURRENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
                   :batchId,:engineVersion,:triggerType,:triggeredBy)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("buildingId", buildingId)
                .addValue("version", rule.versionCode()).addValue("ruleId", rule.ruleId())
                .addValue("score", result.score()).addValue("level", result.level())
                .addValue("available", json(result.availableItems()))
                .addValue("missing", json(result.missingItems()))
                .addValue("suggestions", json(result.suggestions()))
                .addValue("dimensions", json(result.dimensions()))
                .addValue("snapshot", snapshot.toString()).addValue("checksum", checksum)
                .addValue("batchId", batchId).addValue("engineVersion", engineVersion)
                .addValue("triggerType", triggerType).addValue("triggeredBy", triggeredBy));
        jdbc.update("""
                UPDATE core.building SET archive_completeness_score=:score,
                       updated_at=CURRENT_TIMESTAMP
                WHERE id=:buildingId AND deleted_at IS NULL
                """, Map.of("score", result.score(), "buildingId", buildingId));
        return id;
    }

    @Transactional
    public UUID saveRisk(
            UUID buildingId, UUID completenessId, UUID batchId, RuleSnapshot rule,
            String engineVersion, String triggerType, UUID triggeredBy,
            JsonNode snapshot, String checksum, RiskResult result) {
        jdbc.update("""
                UPDATE core.risk_assessment
                SET status='SUPERSEDED', stale_reason='NEW_CURRENT_RESULT', updated_at=CURRENT_TIMESTAMP
                WHERE building_id=:buildingId AND status='CURRENT'
                """, Map.of("buildingId", buildingId));
        UUID id = UUID.randomUUID();
        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("topFactors", result.factors());
        explanation.put("excludedEvidence", result.excludedEvidence());
        explanation.put("missingData", result.missingData());
        explanation.put("recommendations", result.recommendations());
        explanation.put("confidenceLevel", result.confidenceLevel());
        explanation.put("disclaimer", disclaimer());
        jdbc.update("""
                INSERT INTO core.risk_assessment
                  (id, assessment_code, building_id, assessment_version, rule_version_id,
                   completeness_assessment_id, risk_score, confidence_score,
                   evidence_reliability_score, risk_level, dimension_scores, score_explanation,
                   input_snapshot, input_checksum, recommendation, need_manual_review,
                   need_professional_inspection, status, assessed_at, created_at, updated_at,
                   calculation_batch_id, engine_version, trigger_type, triggered_by)
                VALUES
                  (:id,:code,:buildingId,:version,:ruleId,:completenessId,:riskScore,
                   :confidenceScore,:reliabilityScore,:riskLevel,CAST(:dimensions AS jsonb),
                   CAST(:explanation AS jsonb),CAST(:snapshot AS jsonb),:checksum,:recommendation,
                   :manualReview,:professionalInspection,'CURRENT',CURRENT_TIMESTAMP,
                   CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,:batchId,:engineVersion,:triggerType,:triggeredBy)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("code", "RISK-" + id.toString().substring(0, 8))
                .addValue("buildingId", buildingId).addValue("version", rule.versionCode())
                .addValue("ruleId", rule.ruleId()).addValue("completenessId", completenessId)
                .addValue("riskScore", result.riskScore())
                .addValue("confidenceScore", result.confidenceScore())
                .addValue("reliabilityScore", result.evidenceReliabilityScore())
                .addValue("riskLevel", result.riskLevel())
                .addValue("dimensions", json(result.dimensions()))
                .addValue("explanation", json(explanation)).addValue("snapshot", snapshot.toString())
                .addValue("checksum", checksum)
                .addValue("recommendation", String.join("；", result.recommendations()))
                .addValue("manualReview", result.needManualReview())
                .addValue("professionalInspection", result.needProfessionalInspection())
                .addValue("batchId", batchId).addValue("engineVersion", engineVersion)
                .addValue("triggerType", triggerType).addValue("triggeredBy", triggeredBy));
        return id;
    }

    @Transactional
    public UUID saveRenewal(
            UUID buildingId, UUID riskId, UUID batchId, RuleSnapshot rule,
            String engineVersion, String triggerType, UUID triggeredBy,
            String scopeKey, Map<String, Object> scope, JsonNode snapshot,
            String checksum, RenewalResult result) {
        jdbc.update("""
                UPDATE core.renewal_priority
                SET status='SUPERSEDED', stale_reason='NEW_CURRENT_RESULT'
                WHERE building_id=:buildingId AND ranking_scope_key=:scopeKey AND status='CURRENT'
                """, Map.of("buildingId", buildingId, "scopeKey", scopeKey));
        UUID id = UUID.randomUUID();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("factors", result.factors());
        details.put("reliabilityFactor", result.reliabilityFactor());
        details.put("recommendations", result.recommendations());
        details.put("disclaimer", disclaimer());
        jdbc.update("""
                INSERT INTO core.renewal_priority
                  (id, building_id, risk_assessment_id, rule_version_id, priority_version,
                   priority_score, priority_level, ranking, ranking_scope, ranking_scope_key,
                   factor_details, input_snapshot, input_checksum, recommendation, status,
                   generated_at, created_at, calculation_batch_id, engine_version,
                   trigger_type, triggered_by)
                VALUES
                  (:id,:buildingId,:riskId,:ruleId,:version,:score,:level,NULL,
                   CAST(:scope AS jsonb),:scopeKey,CAST(:details AS jsonb),CAST(:snapshot AS jsonb),
                   :checksum,:recommendation,'CURRENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
                   :batchId,:engineVersion,:triggerType,:triggeredBy)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("buildingId", buildingId).addValue("riskId", riskId)
                .addValue("ruleId", rule.ruleId()).addValue("version", rule.versionCode())
                .addValue("score", result.priorityScore()).addValue("level", result.priorityLevel())
                .addValue("scope", json(scope)).addValue("scopeKey", scopeKey)
                .addValue("details", json(details)).addValue("snapshot", snapshot.toString())
                .addValue("checksum", checksum)
                .addValue("recommendation", String.join("；", result.recommendations()))
                .addValue("batchId", batchId).addValue("engineVersion", engineVersion)
                .addValue("triggerType", triggerType).addValue("triggeredBy", triggeredBy));
        return id;
    }

    public List<Map<String, Object>> rankingCandidates(String scopeKey) {
        return jdbc.query("""
                SELECT p.id AS "priorityId", p.building_id AS "buildingId",
                       p.priority_score AS "priorityScore", p.priority_level AS "priorityLevel",
                       p.ranking AS "ranking",
                       r.risk_score AS "riskScore", r.risk_level AS "riskLevel",
                       r.confidence_score AS "confidenceScore",
                       r.need_manual_review AS "needManualReview",
                       r.need_professional_inspection AS "needProfessionalInspection",
                       b.resident_count AS "residentCount", b.building_code AS "buildingCode",
                       b.building_name AS "buildingName", b.community_id AS "communityId",
                       c.community_name AS "communityName", p.ranking_scope_key AS "rankingScopeKey",
                       p.status AS "status", p.generated_at AS "generatedAt",
                       ca.completeness_score AS "completenessScore",
                       ca.completeness_level AS "completenessLevel",
                       p.factor_details AS "factorDetails"
                FROM core.renewal_priority p
                JOIN core.risk_assessment r ON r.id=p.risk_assessment_id AND r.status='CURRENT'
                JOIN core.building b ON b.id=p.building_id AND b.deleted_at IS NULL
                JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
                JOIN core.completeness_assessment ca
                  ON ca.id=r.completeness_assessment_id AND ca.status='CURRENT'
                WHERE p.ranking_scope_key=:scopeKey AND p.status='CURRENT'
                """, Map.of("scopeKey", scopeKey), rowMapper);
    }

    public void updateRankings(List<Map<String, Object>> ordered) {
        int ranking = 1;
        for (Map<String, Object> row : ordered) {
            jdbc.update("UPDATE core.renewal_priority SET ranking=:ranking WHERE id=:id",
                    Map.of("ranking", ranking++, "id", row.get("priorityId")));
        }
    }

    public Map<String, Object> currentBuilding(UUID buildingId) {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> building = one("""
                SELECT b.id AS "buildingId", b.building_code AS "buildingCode",
                       b.building_name AS "buildingName", b.community_id AS "communityId",
                       c.community_name AS "communityName"
                FROM core.building b JOIN core.community c ON c.id=b.community_id
                WHERE b.id=:buildingId AND b.deleted_at IS NULL AND c.deleted_at IS NULL
                """, Map.of("buildingId", buildingId)).orElse(Map.of());
        if (building.isEmpty()) {
            throw new org.urbansafe.priority.common.exception.ResourceNotFoundException(
                    "BUILDING_NOT_FOUND", "楼栋不存在");
        }
        data.putAll(building);
        data.put("completeness", currentCompleteness(buildingId).orElse(null));
        data.put("risk", currentRisk(buildingId).orElse(null));
        data.put("renewalPriorities", currentRenewals(buildingId));
        data.put("freshness", freshness(data.get("completeness"), data.get("risk"),
                (List<?>) data.get("renewalPriorities")));
        data.put("inputSummary", Map.of());
        data.put("disclaimer", disclaimer());
        return data;
    }

    private String freshness(Object completeness, Object risk, List<?> renewals) {
        boolean hasAny = completeness != null || risk != null || !renewals.isEmpty();
        if (!hasAny) return "NO_RESULT";
        boolean completeChain = completeness != null && risk != null && !renewals.isEmpty();
        if (!completeChain
                || isStale(completeness)
                || isStale(risk)
                || renewals.stream().anyMatch(this::isStale)) {
            return "STALE";
        }
        return "CURRENT";
    }

    private boolean isStale(Object value) {
        return value instanceof Map<?, ?> map && "STALE".equals(String.valueOf(map.get("status")));
    }

    public Optional<Map<String, Object>> currentCompleteness(UUID buildingId) {
        return one("""
                SELECT ca.id AS "assessmentId", ca.completeness_score AS "completenessScore",
                       ca.completeness_level AS "completenessLevel", ca.status AS "status",
                       ca.dimension_scores AS "dimensionScores", ca.available_items AS "availableItems",
                       ca.missing_items AS "missingItems", ca.suggestions AS "suggestions",
                       ca.assessed_at AS "assessedAt", rv.version_code AS "ruleVersion",
                       ca.input_checksum AS "inputChecksum", ca.engine_version AS "engineVersion",
                       ca.trigger_type AS "triggerType", ca.stale_reason AS "staleReason",
                       ca.calculation_batch_id AS "calculationBatchId"
                FROM core.completeness_assessment ca
                JOIN core.rule_version rv ON rv.id=ca.rule_version_id
                WHERE ca.building_id=:buildingId AND ca.status IN ('CURRENT', 'STALE')
                ORDER BY CASE ca.status WHEN 'CURRENT' THEN 0 ELSE 1 END, ca.assessed_at DESC
                LIMIT 1
                """, Map.of("buildingId", buildingId))
                .map(row -> normalizeJson(row, "dimensionScores", "availableItems", "missingItems", "suggestions"));
    }

    public Optional<Map<String, Object>> currentRisk(UUID buildingId) {
        return one("""
                SELECT ra.id AS "assessmentId", ra.risk_score AS "riskScore",
                       ra.risk_level AS "riskLevel", ra.confidence_score AS "confidenceScore",
                       CASE WHEN ra.confidence_score>=80 THEN 'HIGH'
                            WHEN ra.confidence_score>=60 THEN 'MEDIUM' ELSE 'LOW' END AS "confidenceLevel",
                       ra.evidence_reliability_score AS "evidenceReliabilityScore",
                       ra.status AS "status", ra.dimension_scores AS "dimensionScores",
                       ra.score_explanation->'topFactors' AS "topFactors",
                       ra.score_explanation->'excludedEvidence' AS "excludedEvidence",
                       ra.score_explanation->'missingData' AS "missingData",
                       ra.score_explanation->'recommendations' AS "recommendations",
                       ra.need_manual_review AS "needManualReview",
                       ra.need_professional_inspection AS "needProfessionalInspection",
                       ra.assessed_at AS "assessedAt", rv.version_code AS "ruleVersion",
                       ra.input_checksum AS "inputChecksum", ra.engine_version AS "engineVersion",
                       ra.trigger_type AS "triggerType", ra.stale_reason AS "staleReason",
                       ra.calculation_batch_id AS "calculationBatchId",
                       :disclaimer AS "disclaimer"
                FROM core.risk_assessment ra
                JOIN core.rule_version rv ON rv.id=ra.rule_version_id
                JOIN core.completeness_assessment ca ON ca.id=ra.completeness_assessment_id
                WHERE ra.building_id=:buildingId AND ra.status IN ('CURRENT', 'STALE')
                  AND (ra.status='STALE' OR ca.status='CURRENT')
                ORDER BY CASE ra.status WHEN 'CURRENT' THEN 0 ELSE 1 END, ra.assessed_at DESC
                LIMIT 1
                """, Map.of("buildingId", buildingId, "disclaimer", disclaimer()))
                .map(row -> normalizeJson(row, "dimensionScores", "topFactors", "excludedEvidence", "missingData", "recommendations"));
    }

    public List<Map<String, Object>> currentRenewals(UUID buildingId) {
        return jdbc.query("""
                SELECT DISTINCT ON (p.ranking_scope_key) p.id AS "priorityId", p.priority_score AS "priorityScore",
                       p.priority_level AS "priorityLevel", p.ranking AS "ranking",
                       p.ranking_scope_key AS "rankingScopeKey", p.status AS "status",
                       p.factor_details->'factors' AS "factors",
                       p.factor_details->'recommendations' AS "recommendations",
                       p.generated_at AS "generatedAt", rv.version_code AS "ruleVersion",
                       p.input_checksum AS "inputChecksum", p.engine_version AS "engineVersion",
                       p.trigger_type AS "triggerType", p.stale_reason AS "staleReason",
                       p.calculation_batch_id AS "calculationBatchId",
                       :disclaimer AS "disclaimer"
                FROM core.renewal_priority p
                JOIN core.rule_version rv ON rv.id=p.rule_version_id
                JOIN core.risk_assessment r ON r.id=p.risk_assessment_id
                JOIN core.completeness_assessment ca ON ca.id=r.completeness_assessment_id
                WHERE p.building_id=:buildingId AND p.status IN ('CURRENT', 'STALE')
                  AND (p.status='STALE' OR (r.status='CURRENT' AND ca.status='CURRENT'))
                ORDER BY p.ranking_scope_key, CASE p.status WHEN 'CURRENT' THEN 0 ELSE 1 END, p.generated_at DESC
                """, Map.of("buildingId", buildingId, "disclaimer", disclaimer()), rowMapper).stream()
                .map(row -> normalizeJson(row, "factors", "recommendations"))
                .toList();
    }

    public List<Map<String, Object>> history(
            UUID buildingId, String assessmentType, int page, int size) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (assessmentType == null || "COMPLETENESS".equals(assessmentType)) {
            rows.addAll(jdbc.query("""
                    SELECT ca.id AS "assessmentId", 'COMPLETENESS' AS "assessmentType",
                           ca.completeness_score AS "score", NULL::numeric AS "secondaryScore",
                           ca.completeness_level AS "level", ca.status AS "status",
                           rv.version_code AS "ruleVersion", ca.input_checksum AS "inputChecksum",
                           ca.engine_version AS "engineVersion", ca.trigger_type AS "triggerType",
                           ca.triggered_by AS "triggeredBy", ca.calculation_batch_id AS "calculationBatchId",
                           ca.assessed_at AS "calculatedAt", ca.stale_reason AS "staleReason",
                           :disclaimer AS "disclaimer"
                    FROM core.completeness_assessment ca JOIN core.rule_version rv ON rv.id=ca.rule_version_id
                    WHERE ca.building_id=:buildingId
                    """, Map.of("buildingId", buildingId, "disclaimer", disclaimer()), rowMapper));
        }
        if (assessmentType == null || "RISK".equals(assessmentType)) {
            rows.addAll(jdbc.query("""
                    SELECT ra.id AS "assessmentId", 'RISK' AS "assessmentType",
                           ra.risk_score AS "score", ra.confidence_score AS "secondaryScore",
                           ra.risk_level AS "level", ra.status AS "status",
                           rv.version_code AS "ruleVersion", ra.input_checksum AS "inputChecksum",
                           ra.engine_version AS "engineVersion", ra.trigger_type AS "triggerType",
                           ra.triggered_by AS "triggeredBy", ra.calculation_batch_id AS "calculationBatchId",
                           ra.assessed_at AS "calculatedAt", ra.stale_reason AS "staleReason",
                           :disclaimer AS "disclaimer"
                    FROM core.risk_assessment ra JOIN core.rule_version rv ON rv.id=ra.rule_version_id
                    WHERE ra.building_id=:buildingId
                    """, Map.of("buildingId", buildingId, "disclaimer", disclaimer()), rowMapper));
        }
        if (assessmentType == null || "RENEWAL".equals(assessmentType)) {
            rows.addAll(jdbc.query("""
                    SELECT p.id AS "assessmentId", 'RENEWAL' AS "assessmentType",
                           p.priority_score AS "score", NULL::numeric AS "secondaryScore",
                           p.priority_level AS "level", p.status AS "status",
                           rv.version_code AS "ruleVersion", p.input_checksum AS "inputChecksum",
                           p.engine_version AS "engineVersion", p.trigger_type AS "triggerType",
                           p.triggered_by AS "triggeredBy", p.calculation_batch_id AS "calculationBatchId",
                           p.generated_at AS "calculatedAt", p.stale_reason AS "staleReason",
                           :disclaimer AS "disclaimer"
                    FROM core.renewal_priority p JOIN core.rule_version rv ON rv.id=p.rule_version_id
                    WHERE p.building_id=:buildingId
                    """, Map.of("buildingId", buildingId, "disclaimer", disclaimer()), rowMapper));
        }
        rows.sort(java.util.Comparator.comparing(
                row -> instant(row.get("calculatedAt")),
                java.util.Comparator.reverseOrder()));
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return List.copyOf(rows.subList(from, to));
    }

    public long historyCount(UUID buildingId, String type) {
        if (type != null) {
            String table = switch (type) {
                case "COMPLETENESS" -> "core.completeness_assessment";
                case "RISK" -> "core.risk_assessment";
                case "RENEWAL" -> "core.renewal_priority";
                default -> throw new IllegalArgumentException("Unknown assessment type");
            };
            return count("SELECT COUNT(*) FROM " + table + " WHERE building_id=:buildingId",
                    Map.of("buildingId", buildingId));
        }
        return count("""
                SELECT (SELECT COUNT(*) FROM core.completeness_assessment WHERE building_id=:buildingId)
                     + (SELECT COUNT(*) FROM core.risk_assessment WHERE building_id=:buildingId)
                     + (SELECT COUNT(*) FROM core.renewal_priority WHERE building_id=:buildingId)
                """, Map.of("buildingId", buildingId));
    }

    public List<UUID> buildingIds(String scopeType, String scopeId, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id FROM core.building b
                JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
                WHERE b.deleted_at IS NULL
                """);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", limit);
        if ("COMMUNITY".equals(scopeType)) {
            sql.append(" AND b.community_id=:scopeId");
            params.addValue("scopeId", UUID.fromString(scopeId));
        } else if ("REGION".equals(scopeType)) {
            sql.append(" AND c.administrative_region=:scopeId");
            params.addValue("scopeId", scopeId);
        }
        sql.append(" ORDER BY b.building_code, b.id LIMIT :limit");
        return jdbc.query(sql.toString(), params, (rs, rowNum) -> rs.getObject(1, UUID.class));
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private long count(String sql, Map<String, ?> params) {
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> normalizeJson(Map<String, Object> row, String... keys) {
        Map<String, Object> normalized = new LinkedHashMap<>(row);
        for (String key : keys) {
            Object value = normalized.get(key);
            if (value == null || value instanceof Map<?, ?> || value instanceof List<?>) continue;
            try {
                normalized.put(key, objectMapper.readValue(String.valueOf(value), Object.class));
            } catch (Exception ex) {
                normalized.put(key, List.of());
            }
        }
        return normalized;
    }

    private java.time.Instant instant(Object value) {
        if (value instanceof java.time.OffsetDateTime time) return time.toInstant();
        if (value instanceof java.sql.Timestamp time) return time.toInstant();
        return java.time.OffsetDateTime.parse(String.valueOf(value)).toInstant();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("评分结果 JSON 序列化失败", ex);
        }
    }

    public static String disclaimer() {
        return "系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。"
                + "对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。";
    }
}
