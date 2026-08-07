package org.urbansafe.priority.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.assessment.repository.AssessmentResultRepository;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class AssessmentCurrentStateIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssessmentResultRepository repository;

    @Test
    void staleCompletenessIsReturnedAsStaleInsteadOfNoResult() {
        UUID buildingId = createBuilding("STALE-COMP");
        UUID ruleId = ruleId("COMPLETENESS", "COMPLETENESS-V1");
        insertCompleteness(buildingId, ruleId, "STALE", "RULE_CHANGED:COMPLETENESS-V1.1");

        Map<String, Object> current = repository.currentBuilding(buildingId);

        assertThat(current.get("freshness")).isEqualTo("STALE");
        assertThat(current.get("completeness")).isInstanceOf(Map.class);
        assertThat(map(current.get("completeness")))
                .containsEntry("status", "STALE")
                .containsEntry("staleReason", "RULE_CHANGED:COMPLETENESS-V1.1");
    }

    @Test
    void mixedCurrentAndStaleResultsProduceStaleOverallFreshness() {
        UUID buildingId = createBuilding("MIXED-STALE");
        UUID completenessRuleId = ruleId("COMPLETENESS", "COMPLETENESS-V1");
        UUID riskRuleId = ruleId("RISK", "RISK-V1");
        UUID renewalRuleId = ruleId("RENEWAL", "RENEWAL-V1.1");
        UUID completenessId = insertCompleteness(buildingId, completenessRuleId, "CURRENT", null);
        UUID riskId = insertRisk(buildingId, riskRuleId, completenessId, "STALE", "RULE_CHANGED:RISK-V2");
        insertRenewal(buildingId, renewalRuleId, riskId, "STALE", "RULE_CHANGED:RENEWAL-V2", "ALL");

        Map<String, Object> current = repository.currentBuilding(buildingId);

        assertThat(current.get("freshness")).isEqualTo("STALE");
        assertThat(map(current.get("risk")))
                .containsEntry("status", "STALE")
                .containsEntry("staleReason", "RULE_CHANGED:RISK-V2");
        assertThat((List<?>) current.get("renewalPriorities")).hasSize(1);
    }

    @Test
    void noAssessmentHistoryProducesNoResultFreshness() {
        UUID buildingId = createBuilding("NO-RESULT");

        Map<String, Object> current = repository.currentBuilding(buildingId);

        assertThat(current.get("freshness")).isEqualTo("NO_RESULT");
        assertThat(current.get("completeness")).isNull();
        assertThat(current.get("risk")).isNull();
        assertThat((List<?>) current.get("renewalPriorities")).isEmpty();
    }

    @Test
    void rankingCandidatesExcludeStaleRenewalResults() {
        UUID buildingId = createBuilding("STALE-RANK");
        UUID completenessRuleId = ruleId("COMPLETENESS", "COMPLETENESS-V1");
        UUID riskRuleId = ruleId("RISK", "RISK-V1");
        UUID renewalRuleId = ruleId("RENEWAL", "RENEWAL-V1.1");
        UUID completenessId = insertCompleteness(buildingId, completenessRuleId, "CURRENT", null);
        UUID riskId = insertRisk(buildingId, riskRuleId, completenessId, "CURRENT", null);
        insertRenewal(buildingId, renewalRuleId, riskId, "STALE", "RULE_CHANGED:RENEWAL-V2", "ALL");

        List<Map<String, Object>> rows = repository.rankingCandidates("ALL");

        assertThat(rows)
                .extracting(row -> row.get("buildingId"))
                .doesNotContain(buildingId);
    }

    private UUID createBuilding(String codePrefix) {
        UUID communityId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO core.community
                  (id, community_code, community_name, administrative_region, building_count,
                   household_count, resident_count, status, extra_attributes)
                VALUES (?, ?, ?, '测试区', 1, 10, 30, 'ACTIVE', '{}'::jsonb)
                """, communityId, "C-" + codePrefix + "-" + suffix, "测试小区-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, ?, '测试路 1 号', 1990, 'BRICK_CONCRETE', 6, 1200,
                        10, 30, 'ACTIVE', '{}'::jsonb)
                """, buildingId, communityId, "B-" + codePrefix + "-" + suffix, "测试楼-" + suffix);
        return buildingId;
    }

    private UUID ruleId(String ruleType, String versionCode) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM core.rule_version
                WHERE rule_type=? AND version_code=? AND deleted_at IS NULL
                """, UUID.class, ruleType, versionCode);
    }

    private UUID insertCompleteness(UUID buildingId, UUID ruleId, String status, String staleReason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.completeness_assessment
                  (id, building_id, assessment_version, rule_version_id, completeness_score,
                   completeness_level, available_items, missing_items, suggestions,
                   input_snapshot, input_checksum, status, assessed_at, created_at,
                   calculation_batch_id, engine_version, trigger_type, stale_reason,
                   dimension_scores)
                VALUES (?, ?, 'TEST-V1', ?, 72.50, 'GOOD', '["基础档案"]'::jsonb,
                        '["专业检测"]'::jsonb, '["补充专业检测"]'::jsonb, '{}'::jsonb,
                        ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, 'phase4-rule-engine-v1',
                        'RULE_CHANGE', ?, '[{"code":"BASIC_ARCHIVE","score":100}]'::jsonb)
                """, id, buildingId, ruleId, checksum(id), status, UUID.randomUUID(), staleReason);
        return id;
    }

    private UUID insertRisk(
            UUID buildingId, UUID ruleId, UUID completenessId, String status, String staleReason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.risk_assessment
                  (id, assessment_code, building_id, assessment_version, rule_version_id,
                   completeness_assessment_id, risk_score, confidence_score,
                   evidence_reliability_score, risk_level, dimension_scores, score_explanation,
                   input_snapshot, input_checksum, recommendation, need_manual_review,
                   need_professional_inspection, status, assessed_at, created_at, updated_at,
                   calculation_batch_id, engine_version, trigger_type, stale_reason)
                VALUES (?, ?, ?, 'TEST-V1', ?, ?, 64.00, 58.00, 70.00, 'HIGH',
                        '[{"code":"BUILDING_BASE","score":64}]'::jsonb,
                        '{"topFactors":[{"factorCode":"AGE","label":"楼龄","effect":20,"direction":"INCREASE"}],"excludedEvidence":[],"missingData":[],"recommendations":["人工复核"]}'::jsonb,
                        '{}'::jsonb, ?, '人工复核', true, true, ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, 'phase4-rule-engine-v1',
                        'RULE_CHANGE', ?)
                """, id, "RISK-" + id.toString().substring(0, 8), buildingId, ruleId,
                completenessId, checksum(id), status, UUID.randomUUID(), staleReason);
        return id;
    }

    private void insertRenewal(
            UUID buildingId, UUID ruleId, UUID riskId, String status, String staleReason, String scopeKey) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.renewal_priority
                  (id, building_id, risk_assessment_id, rule_version_id, priority_version,
                   priority_score, priority_level, ranking, ranking_scope, ranking_scope_key,
                   factor_details, input_snapshot, input_checksum, recommendation, status,
                   generated_at, created_at, calculation_batch_id, engine_version, trigger_type,
                   stale_reason)
                VALUES (?, ?, ?, ?, 'TEST-V1', 66.00, 'P2', 1,
                        '{"scopeType":"ALL"}'::jsonb, ?,
                        '{"factors":[{"code":"RISK","score":66}],"recommendations":["纳入更新评估"]}'::jsonb,
                        '{}'::jsonb, ?, '纳入更新评估', ?, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, ?, 'phase4-rule-engine-v1', 'RULE_CHANGE', ?)
                """, id, buildingId, riskId, ruleId, scopeKey, checksum(id),
                status, UUID.randomUUID(), staleReason);
    }

    private String checksum(UUID id) {
        return id.toString().replace("-", "") + id.toString().replace("-", "");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}

