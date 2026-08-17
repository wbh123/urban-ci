package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

class FeedbackReinspectionLegacyCompatibilityTest {

    @Test
    void omittedDecisionThroughExtendedEndpointKeepsLegacyRequiredBehaviorWithoutOverrideReason() {
        FeedbackRepository repository = mock(FeedbackRepository.class);
        FeedbackClosureRepository closureRepository = mock(FeedbackClosureRepository.class);
        Phase2AssetService assetService = mock(Phase2AssetService.class);
        FeedbackService feedbackService = mock(FeedbackService.class);
        FeedbackClosureService service = new FeedbackClosureService(
                repository,
                closureRepository,
                mock(Phase2InspectionService.class),
                assetService,
                feedbackService);

        UUID reportId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-LEGACY",
                "status", "PROCESSING",
                "reportType", "OTHER",
                "urgency", "NORMAL",
                "description", "公共区域标识脱胶需要重新粘贴",
                "locationText", "一层入口",
                "buildingId", UUID.randomUUID())));
        when(assetService.list("RESIDENT_REPORT", reportId)).thenReturn(List.of(Map.of(
                "assetId", UUID.randomUUID(),
                "bindingRole", "RECTIFICATION_PHOTO")));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.empty());
        when(feedbackService.updateStatus(eq(reportId), anyMap(), eq(actor)))
                .thenReturn(Map.of("reportId", reportId, "status", "RESOLVED"));

        Map<String, Object> result = service.submitRectification(
                reportId,
                "已重新固定标识并完成清理。",
                null,
                null,
                null,
                actor);

        assertThat(result)
                .containsEntry("status", "RESOLVED")
                .containsEntry("reinspectionDecision", "REQUIRED")
                .containsEntry("recommendedDecision", "WAIVED")
                .containsEntry("manualOverride", false)
                .containsEntry("formalRiskChanged", false);
    }
}
