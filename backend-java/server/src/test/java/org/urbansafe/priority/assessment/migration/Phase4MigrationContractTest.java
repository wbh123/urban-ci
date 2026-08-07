package org.urbansafe.priority.assessment.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Phase4MigrationContractTest {

    @Test
    void migrationContainsRealRuleChecksumsAndCurrentResultGuards() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V14__prepare_phase4_assessment_engine.sql")) {
            if (stream == null) {
                throw new IOException("V14 migration not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("e0938f1cbcd38b78ba4471c9c37c70d3d88acf60685e6ad7b9866d616ff7fcdc"));
        assertTrue(sql.contains("47e2b83e321f954c8de2739e5edee4666a533ff57259ba8d31a3da9c9a2da7e9"));
        assertTrue(sql.contains("db49a05db089326987ea2fb38f11a3f71b1c93ae6398be7f6372955970a55140"));
        assertTrue(sql.contains("uk_rule_version_single_active"));
        assertTrue(sql.contains("uk_completeness_current_building"));
        assertTrue(sql.contains("uk_risk_current_building"));
        assertTrue(sql.contains("uk_renewal_current_scope_building"));
        assertTrue(sql.contains("RENAME COLUMN safety_score TO risk_score"));
        assertFalse(sql.contains("TODO_CHECKSUM"));
        assertFalse(sql.contains("PLACEHOLDER_CHECKSUM"));
    }

    @Test
    void renewalV11MigrationContainsRuleDrivenAgeAndRealChecksum() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V15__add_phase4_renewal_rule_v1_1.sql")) {
            if (stream == null) {
                throw new IOException("V15 migration not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("RENEWAL-V1.1"));
        assertTrue(sql.contains("buildingAge"));
        assertTrue(sql.contains("424e4f3ef9626d6c9a5b65cc241c703f485ce6d863ab9972a2504cdbe4997ddd"));
        assertFalse(sql.contains("TODO_CHECKSUM"));
        assertFalse(sql.contains("PLACEHOLDER_CHECKSUM"));
    }
    @Test
    void governanceUrgencyEvidenceConstraintIsAddedInV16() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V16__allow_governance_urgency_evidence.sql")) {
            if (stream == null) {
                throw new IOException("V16 migration not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("building_evidence_evidence_type_check"));
        assertTrue(sql.contains("GOVERNANCE_URGENCY"));
        assertFalse(sql.contains("TODO"));
        assertFalse(sql.contains("PLACEHOLDER"));
    }

    @Test
    void historicalVersionUniqueIndexesAreRelaxedInV17() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V17__relax_phase4_historical_version_uniqueness.sql")) {
            if (stream == null) {
                throw new IOException("V17 migration not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("DROP INDEX IF EXISTS core.uk_completeness_building_version"));
        assertTrue(sql.contains("DROP INDEX IF EXISTS core.uk_risk_building_version"));
        assertTrue(sql.contains("DROP INDEX IF EXISTS core.uk_renewal_building_version"));
        assertTrue(sql.contains("idx_renewal_building_version_scope"));
        assertFalse(sql.contains("TODO"));
        assertFalse(sql.contains("PLACEHOLDER"));
    }


    @Test
    void legacyRenewalCurrentResultsAreStaledInV18() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V18__stale_legacy_renewal_results_after_v1_1.sql")) {
            if (stream == null) {
                throw new IOException("V18 migration not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("RULE_CHANGED:RENEWAL-V1.1"));
        assertTrue(sql.contains("core.renewal_priority"));
        assertTrue(sql.contains("RENEWAL-V1.1"));
        assertTrue(sql.contains("p.status = 'CURRENT'"));
        assertFalse(sql.contains("core.completeness_assessment"));
        assertFalse(sql.contains("core.risk_assessment"));
        assertFalse(sql.contains("TODO"));
        assertFalse(sql.contains("PLACEHOLDER"));
    }


    @Test
    void completenessV11MigrationContainsRuleDrivenRecencyAndChecksum() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V19__add_phase4_completeness_rule_v1_1.sql")) {
            if (stream == null) {
                throw new IOException("V19 migration not found");
            }
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("COMPLETENESS-V1.1"));
        assertTrue(sql.contains("evidenceRecency"));
        assertTrue(sql.contains("maintenanceYears"));
        assertTrue(sql.contains("professionalInspectionYears"));
        assertTrue(sql.contains("cc528dc0e116ebbcde636db751834be4173d13a31911648c176de841751b4a2a"));
        assertTrue(sql.contains("RULE_CHANGED:COMPLETENESS-V1.1"));
        assertFalse(sql.contains("TODO_CHECKSUM"));
        assertFalse(sql.contains("PLACEHOLDER_CHECKSUM"));
    }

}
