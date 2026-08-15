package org.urbansafe.priority.ai.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.report.controller.ReportDashboardService;

@ExtendWith(MockitoExtension.class)
class AiDashboardServiceTest {

    @Mock
    private AiDashboardRepository repository;

    @Mock
    private ReportDashboardService reportDashboardService;

    private AiDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AiDashboardService(repository, reportDashboardService);
    }

    @Test
    void overviewAggregatesExistingFactsWithoutChangingFormalRiskSemantics() {
        UUID highRiskId = UUID.randomUUID();
        UUID staleId = UUID.randomUUID();
        when(reportDashboardService.riskMap("ALL", null)).thenReturn(Map.of(
                "buildings", List.of(
                        Map.of(
                                "buildingId", highRiskId,
                                "buildingCode", "A-01",
                                "buildingName", "A栋",
                                "communityName", "城安小区",
                                "riskLevel", "HIGH",
                                "riskScore", 76.3,
                                "priorityLevel", "P1",
                                "freshness", "CURRENT"),
                        Map.of(
                                "buildingId", staleId,
                                "buildingCode", "B-02",
                                "buildingName", "B栋",
                                "communityName", "城安小区",
                                "riskLevel", "MEDIUM",
                                "riskScore", 52.0,
                                "priorityLevel", "P2",
                                "freshness", "STALE"))));
        when(repository.metrics()).thenReturn(Map.of(
                "aiAnalyzedImageCount", 23L,
                "aiAnalyzedBuildingCount", 2L,
                "detectionCount", 8L,
                "pendingReviewCount", 1L));
        when(repository.todayMetrics()).thenReturn(Map.of(
                "totalAnalyses", 5L,
                "succeeded", 3L,
                "running", 1L,
                "failed", 1L,
                "crackCount", 2L,
                "spallingCount", 1L,
                "waterStainCount", 0L,
                "otherDetectionCount", 1L));
        when(repository.buildingAiRows()).thenReturn(List.of(
                Map.of(
                        "buildingId", highRiskId,
                        "visualCount", 3L,
                        "pendingReviewCount", 1L,
                        "archiveCount", 2L,
                        "inspectionCount", 4L,
                        "latestAiAt", OffsetDateTime.parse("2026-08-14T05:00:00Z"),
                        "latestAiSummary", "南侧外墙发现疑似裂缝",
                        "latestInspectionAt", OffsetDateTime.parse("2026-08-14T04:00:00Z")),
                Map.of(
                        "buildingId", staleId,
                        "visualCount", 1L,
                        "pendingReviewCount", 0L,
                        "archiveCount", 1L,
                        "inspectionCount", 2L,
                        "latestAiAt", OffsetDateTime.parse("2026-08-13T05:00:00Z"))));
        when(repository.latestFindings()).thenReturn(List.of(
                Map.of(
                        "buildingId", highRiskId,
                        "classCode", "CRACK",
                        "className", "疑似裂缝",
                        "count", 2L,
                        "maxConfidence", 0.87)));

        Map<String, Object> overview = service.overview();
        Map<String, Object> metrics = cast(overview.get("metrics"));
        List<Map<String, Object>> attention = list(overview.get("attention"));

        assertEquals(2L, metrics.get("buildingCount"));
        assertEquals(1L, metrics.get("highRiskCount"));
        assertEquals(100.0, metrics.get("analysisCoverageRate"));
        assertEquals("HIGH", attention.get(0).get("riskLevel"));
        assertEquals(76.3, attention.get(0).get("riskScore"));
        assertEquals("HIGH", attention.get(0).get("aiAttentionLevel"));
        assertTrue(listOfStrings(attention.get(0).get("aiAttentionReasons")).contains("正式高风险"));
        assertTrue(listOfStrings(attention.get(0).get("aiAttentionReasons")).contains("待人工复核"));
        assertTrue(listOfStrings(attention.get(0).get("aiAttentionReasons")).contains("新增 AI 病害"));
    }

    @Test
    void activityUsesRealEventRowsAndBusinessCopy() {
        UUID buildingId = UUID.randomUUID();
        when(repository.activityRows(10)).thenReturn(List.of(
                Map.of(
                        "eventId", "inference-1",
                        "eventType", "AI_ANALYSIS",
                        "occurredAt", OffsetDateTime.parse("2026-08-14T05:08:00Z"),
                        "status", "SUCCEEDED",
                        "buildingId", buildingId,
                        "buildingName", "A栋",
                        "communityName", "城安小区",
                        "detectionCount", 2L),
                Map.of(
                        "eventId", "review-1",
                        "eventType", "AI_REVIEW",
                        "occurredAt", OffsetDateTime.parse("2026-08-14T05:04:00Z"),
                        "status", "CONFIRMED",
                        "buildingId", buildingId,
                        "buildingName", "A栋",
                        "communityName", "城安小区")));

        Map<String, Object> response = service.activity(10);
        List<Map<String, Object>> items = list(response.get("items"));

        assertEquals("完成 城安小区 · A栋 图片分析", items.get(0).get("title"));
        assertEquals("发现疑似病害 2 处", items.get(0).get("description"));
        assertEquals("完成 城安小区 · A栋 人工复核", items.get(1).get("title"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> listOfStrings(Object value) {
        return (List<String>) value;
    }
}
