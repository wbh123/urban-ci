package org.urbansafe.priority.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class AiDefaultModelSettingsMigrationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void yoloxShouldBeBusinessApprovedButRemainRuntimeValidationCandidate() {
        Map<String, Object> model = jdbcTemplate.queryForMap("""
                SELECT status, mode, deployment_stage, formal_evidence_enabled,
                       quality_status, weight_sha256
                FROM ai.model_registry
                WHERE model_code='AI-BUILDING-YOLOX-001'
                """);

        assertThat(model.get("status")).isEqualTo("APPROVED");
        assertThat(model.get("mode")).isEqualTo("REAL");
        assertThat(model.get("deployment_stage")).isEqualTo("VALIDATING");
        assertThat(model.get("formal_evidence_enabled")).isEqualTo(false);
        assertThat(model.get("quality_status")).isEqualTo("VALIDATING");
        assertThat(model.get("weight_sha256")).isNull();
    }

    @Test
    void defaultVisionModelShouldStartFromExistingLocalVisionModel() {
        String modelId = jdbcTemplate.queryForObject("""
                SELECT text_value
                FROM ai.governance_setting
                WHERE setting_key='DEFAULT_VISION_MODEL_ID'
                """, String.class);

        assertThat(modelId).isEqualTo("AI-VISION-LOCAL-001");
    }
}
