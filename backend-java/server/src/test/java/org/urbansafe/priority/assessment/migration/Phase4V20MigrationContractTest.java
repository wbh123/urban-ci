package org.urbansafe.priority.assessment.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** V20 必须补齐完整度规则升级后的下游结果级联过期。 */
class Phase4V20MigrationContractTest {

    @Test
    void v20CascadesStaleStateToRiskAndRenewalOnly() throws IOException {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V20__cascade_stale_after_completeness_v1_1.sql")) {
            assertNotNull(stream, "V20 migration not found");
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("core.risk_assessment"));
            assertTrue(sql.contains("core.renewal_priority"));
            assertTrue(sql.contains("RULE_CHANGED:COMPLETENESS-V1.1"));
            assertTrue(sql.contains("status = 'CURRENT'"));
            assertFalse(sql.contains("UPDATE core.completeness_assessment"));
            assertFalse(sql.contains("TODO"));
            assertFalse(sql.contains("PLACEHOLDER"));
        }
    }
}
