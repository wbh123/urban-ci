package org.urbansafe.priority.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证第七阶段图片分析工作流 V34 登记迁移，不与空间 R2 的 V33 平台断言耦合。 */
class DifyWorkflowRegistryMigrationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v34ShouldBeAppliedAfterSpatialV33() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(versions).contains("33", "34");
        assertThat(versions.indexOf("34")).isGreaterThan(versions.indexOf("33"));
    }

    @Test
    void v34ShouldSyncImageWorkflowWithoutPromotingEvidenceStatus() {
        Map<String, Object> model = jdbcTemplate.queryForMap("""
                SELECT model_version, deployment_stage, quality_status, formal_evidence_enabled,
                       quality_summary ->> 'difyWorkflowVersion' AS workflow_version
                FROM ai.model_registry
                WHERE model_code = 'AI-DIFY-WORKFLOW-001'
                """);

        assertThat(model.get("model_version")).isEqualTo("image-analysis-v1.1.0");
        assertThat(model.get("workflow_version")).isEqualTo("image-analysis-v1.1.0");
        assertThat(model.get("deployment_stage")).isEqualTo("VALIDATING");
        assertThat(model.get("quality_status")).isEqualTo("VALIDATING");
        assertThat(model.get("formal_evidence_enabled")).isEqualTo(false);
    }
}
