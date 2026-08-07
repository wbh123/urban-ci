package org.urbansafe.priority.assessment.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 使用真实 PostgreSQL 验证 V20 只级联过期旧完整度规则的下游结果。 */
class Phase4V20DataMigrationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v20StalesLegacyDependentRiskAndRenewalButKeepsV11ChainCurrent() throws IOException {
        UUID legacyBuilding = createBuilding("V20-OLD");
        UUID currentBuilding = createBuilding("V20-NEW");
        UUID completenessV1 = ruleId("COMPLETENESS", "COMPLETENESS-V1");
        UUID completenessV11 = ruleId("COMPLETENESS", "COMPLETENESS-V1.1");
        UUID riskRule = ruleId("RISK", "RISK-V1");
        UUID renewalRule = ruleId("RENEWAL", "RENEWAL-V1.1");

        UUID legacyCompleteness = insertCompleteness(
                legacyBuilding, completenessV1, "STALE", "RULE_CHANGED:COMPLETENESS-V1.1");
        UUID legacyRisk = insertRisk(legacyBuilding, riskRule, legacyCompleteness, "CURRENT", null);
        UUID legacyRenewal = insertRenewal(legacyBuilding, renewalRule, legacyRisk, "CURRENT", null);

        UUID currentCompleteness = insertCompleteness(currentBuilding, completenessV11, "CURRENT", null);
        UUID currentRisk = insertRisk(currentBuilding, riskRule, currentCompleteness, "CURRENT", null);
        UUID currentRenewal = insertRenewal(currentBuilding, renewalRule, currentRisk, "CURRENT", null);

        jdbcTemplate.execute(readMigration());

        assertThat(status("core.risk_assessment", legacyRisk)).isEqualTo("STALE");
        assertThat(staleReason("core.risk_assessment", legacyRisk))
                .isEqualTo("RULE_CHANGED:COMPLETENESS-V1.1");
        assertThat(status("core.renewal_priority", legacyRenewal)).isEqualTo("STALE");
        assertThat(staleReason("core.renewal_priority", legacyRenewal))
                .isEqualTo("RULE_CHANGED:COMPLETENESS-V1.1");

        assertThat(status("core.risk_assessment", currentRisk)).isEqualTo("CURRENT");
        assertThat(staleReason("core.risk_assessment", currentRisk)).isNull();
        assertThat(status("core.renewal_priority", currentRenewal)).isEqualTo("CURRENT");
        assertThat(staleReason("core.renewal_priority", currentRenewal)).isNull();
    }

    private String readMigration() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V20__cascade_stale_after_completeness_v1_1.sql")) {
            if (stream == null) throw new IOException("V20 migration not found");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private UUID createBuilding(String prefix) {
        UUID communityId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO core.community
                  (id, community_code, community_name, administrative_region, building_count,
                   household_count, resident_count, status, extra_attributes)
                VALUES (?, ?, ?, 'V20测试区', 1, 10, 30, 'ACTIVE', '{}'::jsonb)
                """, communityId, "C-" + prefix + "-" + suffix, "V20测试小区-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, ?, 'V20测试路 1 号', 1990, 'BRICK_CONCRETE', 6, 1200,
                        10, 30, 'ACTIVE', '{}'::jsonb)
                """, buildingId, communityId, "B-" + prefix + "-" + suffix, "V20测试楼-" + suffix);
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
                VALUES (?, ?, 'TEST-V1', ?, 72.50, 'GOOD', '[]'::jsonb, '[]'::jsonb,
                        '[]'::jsonb, '{}'::jsonb, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        ?, 'phase4-rule-engine-v1', 'RULE_CHANGE', ?, '[]'::jsonb)
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
                VALUES (?, ?, ?, 'TEST-V1', ?, ?, 64, 58, 70, 'HIGH', '[]'::jsonb,
                        '{"topFactors":[],"excludedEvidence":[],"missingData":[],"recommendations":[]}'::jsonb,
                        '{}'::jsonb, ?, '', true, true, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, ?, 'phase4-rule-engine-v1', 'RULE_CHANGE', ?)
                """, id, "RISK-" + id.toString().substring(0, 8), buildingId, ruleId,
                completenessId, checksum(id), status, UUID.randomUUID(), staleReason);
        return id;
    }

    private UUID insertRenewal(
            UUID buildingId, UUID ruleId, UUID riskId, String status, String staleReason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.renewal_priority
                  (id, building_id, risk_assessment_id, rule_version_id, priority_version,
                   priority_score, priority_level, ranking, ranking_scope, ranking_scope_key,
                   factor_details, input_snapshot, input_checksum, recommendation, status,
                   generated_at, created_at, calculation_batch_id, engine_version, trigger_type,
                   stale_reason)
                VALUES (?, ?, ?, ?, 'TEST-V1', 66, 'P2', 1, '{"scopeType":"ALL"}'::jsonb,
                        'ALL', '{"factors":[],"recommendations":[]}'::jsonb, '{}'::jsonb,
                        ?, '', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?,
                        'phase4-rule-engine-v1', 'RULE_CHANGE', ?)
                """, id, buildingId, riskId, ruleId, checksum(id), status, UUID.randomUUID(), staleReason);
        return id;
    }

    private String status(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM " + table + " WHERE id=?", String.class, id);
    }

    private String staleReason(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT stale_reason FROM " + table + " WHERE id=?", String.class, id);
    }

    private String checksum(UUID id) {
        return id.toString().replace("-", "") + id.toString().replace("-", "");
    }
}
