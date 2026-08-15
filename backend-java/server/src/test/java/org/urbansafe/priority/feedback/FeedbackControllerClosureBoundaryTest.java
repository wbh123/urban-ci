package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.feedback.controller.FeedbackController;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackManagementQueryService;
import org.urbansafe.priority.feedback.service.FeedbackService;

class FeedbackControllerClosureBoundaryTest {
    private final FeedbackController controller = new FeedbackController(
            mock(FeedbackService.class), mock(FeedbackManagementQueryService.class), mock(FeedbackClosureService.class));

    @Test void genericStatusCannotSkipRectificationEvidence() {
        assertThatThrownBy(() -> controller.updateStatus(UUID.randomUUID(), Map.of("status","RESOLVED","handlingSummary","绕过整改证据")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("整改");
    }
    @Test void genericStatusCannotSkipReinspection() {
        assertThatThrownBy(() -> controller.updateStatus(UUID.randomUUID(), Map.of("status","CLOSED","handlingSummary","绕过复查复验")))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("复查复验");
    }
}
