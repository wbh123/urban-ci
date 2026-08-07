package org.urbansafe.priority.report.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 第五阶段报告接口响应转换测试。 */
@WithMockUser(username = "report-controller-test", roles = "ADMIN")
class ReportDashboardControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportDashboardService reportDashboardService;

    @MockitoBean
    private AssessmentAccessService assessmentAccessService;

    @Test
    void listReportsShouldIgnoreRepositoryOnlyFields() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportId", reportId);
        row.put("reportCode", "RPT-TEST-001");
        row.put("buildingId", buildingId);
        row.put("buildingCode", "B-01");
        row.put("buildingName", "演示楼栋");
        row.put("communityId", communityId);
        row.put("communityName", "演示社区");
        row.put("reportStatus", "FAILED");
        row.put("reportFormat", "PDF");
        row.put("templateVersion", "phase5-report-v1");
        row.put("sourceChecksum", "a".repeat(64));
        row.put("createdAt", OffsetDateTime.parse("2026-07-26T00:00:00Z"));
        row.put("generatedAt", null);
        row.put("riskLevel", "HIGH");
        row.put("priorityLevel", "P1");
        row.put("reportSummaryJson", "{}");
        row.put("reportSnapshotJson", "{}");
        row.put("bucketName", null);
        row.put("objectKey", null);
        row.put("originalFilename", null);
        row.put("storageProvider", null);

        when(reportDashboardService.list(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Map.of(
                        "content", List.of(row),
                        "page", Map.of(
                                "page", 0,
                                "size", 10,
                                "totalElements", 1,
                                "totalPages", 1)));

        mockMvc.perform(get("/api/v1/risk-reports")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reportId").value(reportId.toString()))
                .andExpect(jsonPath("$.data.content[0].reportStatus").value("FAILED"));
    }
}
