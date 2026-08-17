package org.urbansafe.priority.ai.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.command.ReviewCommand;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.assessment.service.AssessmentInvalidationService;
import org.urbansafe.priority.common.exception.InvalidRequestException;

class AiReviewApplicationServiceTest {

    @Test
    void normalizesAndPersistsReviewedAuxiliaryRiskLevelAndStalesFormalAssessments() {
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiReviewCorrectionRepository repository = mock(AiReviewCorrectionRepository.class);
        AssessmentInvalidationService invalidationService = mock(AssessmentInvalidationService.class);
        AiReviewApplicationService service = new AiReviewApplicationService(
                inferenceService, repository, invalidationService);
        UUID inferenceId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewCommand command = new ReviewCommand(
                inferenceId,
                "CORRECTED",
                "人工修正辅助风险度",
                reviewerId,
                Map.of("reviewedRiskLevel", "high"));
        when(inferenceService.review(command)).thenReturn(Map.of(
                "inferenceId", inferenceId,
                "reviewStatus", "CORRECTED"));
        when(repository.updateLatest(eq(inferenceId), eq(reviewerId), eq(Map.of("reviewedRiskLevel", "HIGH"))))
                .thenReturn(1);
        when(invalidationService.invalidateAfterAiReview(inferenceId)).thenReturn(true);

        Map<String, Object> result = service.review(command);

        verify(repository).updateLatest(inferenceId, reviewerId, Map.of("reviewedRiskLevel", "HIGH"));
        verify(invalidationService).invalidateAfterAiReview(inferenceId);
        assertThat(result.get("correctedData")).isEqualTo(Map.of("reviewedRiskLevel", "HIGH"));
        assertThat(result.get("assessmentRefreshRequired")).isEqualTo(true);
    }

    @Test
    void returnsNoRefreshRequirementWhenInferenceIsNotBoundToBuilding() {
        AiInferenceService inferenceService = mock(AiInferenceService.class);
        AiReviewCorrectionRepository repository = mock(AiReviewCorrectionRepository.class);
        AssessmentInvalidationService invalidationService = mock(AssessmentInvalidationService.class);
        AiReviewApplicationService service = new AiReviewApplicationService(
                inferenceService, repository, invalidationService);
        UUID inferenceId = UUID.randomUUID();
        ReviewCommand command = new ReviewCommand(
                inferenceId, "CONFIRMED", "确认", UUID.randomUUID(), Map.of());
        when(inferenceService.review(command)).thenReturn(Map.of(
                "inferenceId", inferenceId,
                "reviewStatus", "CONFIRMED"));
        when(invalidationService.invalidateAfterAiReview(inferenceId)).thenReturn(false);

        Map<String, Object> result = service.review(command);

        verify(invalidationService).invalidateAfterAiReview(inferenceId);
        assertThat(result.get("assessmentRefreshRequired")).isEqualTo(false);
    }

    @Test
    void rejectsFormalScoreLikeOrUnknownRiskValues() {
        assertThatThrownBy(() -> AiReviewApplicationService.normalizeCorrectedData(
                Map.of("reviewedRiskLevel", "92")))
                .isInstanceOf(InvalidRequestException.class);
    }
}
