package org.urbansafe.priority.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.assessment.checksum.AssessmentChecksumService;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;
import org.urbansafe.priority.common.exception.InvalidRequestException;

class ReportDashboardComponentTest {

    @Test
    void pdfRendererProducesStandardPdfEnvelope() {
        PdfReportRenderer renderer = new PdfReportRenderer();
        byte[] pdf = renderer.render(
                "RPT-TEST-001",
                Map.of(
                        "building", Map.of(
                                "buildingCode", "B-001",
                                "buildingName", "演示一号楼",
                                "communityName", "演示小区"),
                        "assessment", Map.of(
                                "risk", Map.of("riskScore", 82.5, "riskLevel", "VERY_HIGH"),
                                "completeness", Map.of("completenessScore", 76.0),
                                "renewalPriorities", List.of(Map.of(
                                        "priorityScore", 88.0,
                                        "priorityLevel", "P1")))),
                "仅用于风险筛查与辅助决策");

        String prefix = new String(pdf, 0, 8, StandardCharsets.ISO_8859_1);
        String suffix = new String(
                pdf, Math.max(0, pdf.length - 32), Math.min(32, pdf.length),
                StandardCharsets.ISO_8859_1);

        assertThat(prefix).isEqualTo("%PDF-1.4");
        assertThat(suffix).contains("%%EOF");
        assertThat(pdf.length).isGreaterThan(1_000);
    }

    @Test
    void scopeParserBuildsStableKeysAndTypedCommunityParameter() {
        UUID communityId = UUID.randomUUID();

        Scope all = Scope.parse("all", null);
        Scope region = Scope.parse("REGION", "天心区");
        Scope community = Scope.parse("community", communityId.toString());

        assertThat(all.key()).isEqualTo("ALL");
        assertThat(region.key()).isEqualTo("REGION:天心区");
        assertThat(community.key()).isEqualTo("COMMUNITY:" + communityId);
        assertThat(community.params().getValue("communityId")).isEqualTo(communityId);
    }

    @Test
    void scopeParserRejectsMissingOrMalformedScopeIdentifiers() {
        assertThatThrownBy(() -> Scope.parse("REGION", " "))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Scope.parse("COMMUNITY", "not-a-uuid"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> Scope.parse("CITY", "demo"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void overviewShouldTolerateRowsWithoutAssessmentEnums() {
        ReportDashboardRepository repository = mock(ReportDashboardRepository.class);
        ReportDashboardService service = new ReportDashboardService(
                repository,
                mock(AssessmentApplicationService.class),
                mock(AssessmentChecksumService.class),
                new ObjectMapper(),
                mock(ReportStorageService.class));

        UUID assessedBuildingId = UUID.randomUUID();
        UUID unassessedBuildingId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        Map<String, Object> unassessed = new LinkedHashMap<>();
        unassessed.put("buildingId", unassessedBuildingId);
        unassessed.put("communityId", communityId);
        unassessed.put("riskLevel", null);
        unassessed.put("priorityLevel", null);
        unassessed.put("riskScore", null);
        unassessed.put("priorityScore", null);
        unassessed.put("confidenceScore", null);
        unassessed.put("completenessScore", null);
        unassessed.put("freshness", "NO_RESULT");
        unassessed.put("needManualReview", false);
        unassessed.put("ranking", null);

        Map<String, Object> assessed = new LinkedHashMap<>();
        assessed.put("buildingId", assessedBuildingId);
        assessed.put("communityId", communityId);
        assessed.put("riskLevel", "HIGH");
        assessed.put("priorityLevel", "P2");
        assessed.put("riskScore", 72.0);
        assessed.put("priorityScore", 81.0);
        assessed.put("confidenceScore", 65.0);
        assessed.put("completenessScore", 70.0);
        assessed.put("freshness", "CURRENT");
        assessed.put("needManualReview", false);
        assessed.put("ranking", 1);

        when(repository.dashboardRows(any())).thenReturn(List.of(unassessed, assessed));

        Map<String, Object> result = service.overview("ALL", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertThat(summary.get("buildingCount")).isEqualTo(2L);
        assertThat(summary.get("assessedBuildingCount")).isEqualTo(1L);
        assertThat(summary.get("highRiskCount")).isEqualTo(1L);
        assertThat(summary.get("highPriorityCount")).isEqualTo(1L);
        assertThat(summary.get("noResultCount")).isEqualTo(1L);
    }
}
