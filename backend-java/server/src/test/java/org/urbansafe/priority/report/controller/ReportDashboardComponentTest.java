package org.urbansafe.priority.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
}
