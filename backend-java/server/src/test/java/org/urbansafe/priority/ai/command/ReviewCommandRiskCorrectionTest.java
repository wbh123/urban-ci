package org.urbansafe.priority.ai.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewCommandRiskCorrectionTest {

    @Test
    void carriesStructuredCorrectedDataForReviewedAuxiliaryRiskLevel() {
        UUID inferenceId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewCommand command = new ReviewCommand(
                inferenceId,
                "CORRECTED",
                "人工复核后调整辅助风险度",
                reviewerId,
                Map.of("reviewedRiskLevel", "HIGH"));

        assertThat(command.correctedData()).containsEntry("reviewedRiskLevel", "HIGH");
    }
}
