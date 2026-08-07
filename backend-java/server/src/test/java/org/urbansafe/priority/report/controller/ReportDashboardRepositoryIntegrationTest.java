package org.urbansafe.priority.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证第五阶段查询在 V1～V22 真实 PostgreSQL 结构上可执行。 */
class ReportDashboardRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private ReportDashboardRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dashboardAndReportQueriesShouldMatchCurrentDatabaseSchema() {
        UUID missingBuildingId = UUID.randomUUID();

        assertThat(repository.dashboardRows(Scope.parse("ALL", null))).isNotNull();
        assertThat(repository.inspections(missingBuildingId)).isEmpty();
        assertThat(repository.evidence(missingBuildingId)).isEmpty();
        assertThat(repository.aiEvidence(missingBuildingId)).isEmpty();
        assertThat(repository.list(null, null, null, 0, 20)).isNotNull();
        assertThat(repository.countReports(null, null, null)).isGreaterThanOrEqualTo(0);
        assertThatThrownBy(() -> repository.building(missingBuildingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void failedReportShouldNotBlockSameSourceRetry() {
        UUID communityId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID failedReportId = UUID.randomUUID();
        UUID retryReportId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String checksum = "a".repeat(64);
        String idempotencyKey = buildingId + ":" + ReportDashboardService.TEMPLATE_VERSION + ":" + checksum;

        jdbcTemplate.update("""
                INSERT INTO core.community(id, community_code, community_name)
                VALUES (?, ?, ?)
                """, communityId, "C-RPT-" + suffix, "报告测试小区");
        jdbcTemplate.update("""
                INSERT INTO core.building(id, community_id, building_code, building_name)
                VALUES (?, ?, ?, ?)
                """, buildingId, communityId, "B-RPT-" + suffix, "报告测试楼");
        jdbcTemplate.update("""
                INSERT INTO asset.generated_report(
                    id, report_code, report_type, community_id, building_id,
                    report_status, report_summary, data_version, template_version,
                    report_format, source_checksum, idempotency_key, report_snapshot)
                VALUES (?, ?, 'BUILDING_RISK_REPORT', ?, ?, 'FAILED', '{}'::jsonb,
                        substring(? from 1 for 16), ?, 'PDF', ?, ?, '{}'::jsonb)
                """, failedReportId, "RPT-FAILED-" + suffix, communityId, buildingId,
                checksum, ReportDashboardService.TEMPLATE_VERSION, checksum, idempotencyKey);

        repository.createGenerating(
                retryReportId,
                "RPT-RETRY-" + suffix,
                buildingId,
                communityId,
                null,
                null,
                checksum,
                idempotencyKey,
                "{}",
                "{}",
                null,
                "RISK-V1",
                "RENEWAL-V1.1");

        assertThat(repository.report(retryReportId))
                .containsEntry("reportStatus", "GENERATING")
                .containsEntry("sourceChecksum", checksum);
    }

}
