package org.urbansafe.priority.ai.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.model.dto.AiDashboardOverviewSuccessResponse;

class AiDashboardControllerTest {

    @Test
    void overviewKeepsSuccessEnvelope() {
        AiDashboardService service = mock(AiDashboardService.class);
        when(service.overview()).thenReturn(Map.of(
                "generatedAt", "2026-08-14T05:00:00Z",
                "metrics", Map.of(
                        "buildingCount", 8,
                        "aiAnalyzedBuildingCount", 0,
                        "aiAnalyzedImageCount", 0,
                        "detectionCount", 0,
                        "highRiskCount", 0,
                        "pendingReviewCount", 0,
                        "inspectionAttentionCount", 0,
                        "dataIssueCount", 0,
                        "analysisCoverageRate", 0.0),
                "today", Map.of(
                        "totalAnalyses", 0,
                        "succeeded", 0,
                        "running", 0,
                        "failed", 0,
                        "crackCount", 0,
                        "spallingCount", 0,
                        "waterStainCount", 0,
                        "otherDetectionCount", 0),
                "attention", List.of()));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AiDashboardController controller = new AiDashboardController(service, objectMapper);

        ResponseEntity<AiDashboardOverviewSuccessResponse> response = controller.getAiDashboardOverview();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().getSuccess());
        assertNotNull(response.getBody().getData());
    }

    @Test
    void globalAiDashboardReusesExistingRiskDashboardAuthorizationBoundary() throws Exception {
        Method method = AiDashboardController.class.getMethod("getAiDashboardOverview");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals(AssessmentAccessService.BATCH_AND_RANKING_ROLES, preAuthorize.value());
    }
}
