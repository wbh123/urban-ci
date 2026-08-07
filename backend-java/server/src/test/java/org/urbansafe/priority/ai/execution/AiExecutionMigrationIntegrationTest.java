package org.urbansafe.priority.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 第七阶段第二轮数据库结构与种子数据验收。 */
class AiExecutionMigrationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v31ShouldCreateExecutionWorkflowAndKnowledgeTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_schema || '.' || table_name
                FROM information_schema.tables
                WHERE (table_schema='ai' AND table_name IN ('workflow_definition','execution_task'))
                   OR (table_schema='knowledge' AND table_name IN ('document','chunk','question','citation'))
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "ai.workflow_definition",
                "ai.execution_task",
                "knowledge.document",
                "knowledge.chunk",
                "knowledge.question",
                "knowledge.citation");
    }

    @Test
    void v31ShouldSeedControlledWorkflowDefinitions() {
        List<String> codes = jdbc.queryForList("""
                SELECT workflow_code
                FROM ai.workflow_definition
                WHERE workflow_code IN (
                    'DIFY-IMAGE-ANALYSIS-001',
                    'DIFY-REVIEW-ASSIST-001',
                    'DIFY-REPORT-DRAFT-001',
                    'DIFY-KNOWLEDGE-QA-001',
                    'LOCAL-IMAGE-QUALITY-001',
                    'LOCAL-DEFECT-SEGMENTATION-001')
                """, String.class);

        assertThat(codes).containsExactlyInAnyOrder(
                "DIFY-IMAGE-ANALYSIS-001",
                "DIFY-REVIEW-ASSIST-001",
                "DIFY-REPORT-DRAFT-001",
                "DIFY-KNOWLEDGE-QA-001",
                "LOCAL-IMAGE-QUALITY-001",
                "LOCAL-DEFECT-SEGMENTATION-001");
    }
}
