package org.urbansafe.priority.assessment.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.assessment.AssessmentTestFixtures;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;

class FeedbackSignalCalculatorTest {

    private final FeedbackSignalCalculator calculator = new FeedbackSignalCalculator();

    @Test
    void sameTypeThirtyDayWindowCountsAtMostThree() throws Exception {
        var base = AssessmentTestFixtures.fullInput();
        var reports = new ArrayList<BuildingAssessmentInput.FeedbackEvidence>();
        for (int index = 0; index < 8; index++) {
            reports.add(new BuildingAssessmentInput.FeedbackEvidence(
                    java.util.UUID.randomUUID(), "WALL_CRACK", "URGENT", "SUBMITTED",
                    OffsetDateTime.of(2026, 7, 20 - index, 0, 0, 0, 0, ZoneOffset.UTC)));
        }
        var input = new BuildingAssessmentInput(
                base.building(), base.community(), base.geometryAvailable(), base.inspections(),
                base.availableImageCount(), base.imageParts(), base.businessEvidence(), reports,
                base.eligibleAiEvidence(), base.excludedAiEvidence(), base.spatialMetrics(),
                base.calculationDate());

        assertThat(calculator.calculate(input, AssessmentTestFixtures.riskRule()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void rejectedAndCancelledReportsContributeZero() throws Exception {
        var base = AssessmentTestFixtures.fullInput();
        var reports = java.util.List.of(
                new BuildingAssessmentInput.FeedbackEvidence(
                        java.util.UUID.randomUUID(), "WALL_CRACK", "URGENT", "REJECTED",
                        OffsetDateTime.of(2026, 7, 20, 0, 0, 0, 0, ZoneOffset.UTC)),
                new BuildingAssessmentInput.FeedbackEvidence(
                        java.util.UUID.randomUUID(), "WALL_CRACK", "URGENT", "CANCELLED",
                        OffsetDateTime.of(2026, 7, 20, 0, 0, 0, 0, ZoneOffset.UTC)));
        var input = new BuildingAssessmentInput(
                base.building(), base.community(), base.geometryAvailable(), base.inspections(),
                base.availableImageCount(), base.imageParts(), base.businessEvidence(), reports,
                base.eligibleAiEvidence(), base.excludedAiEvidence(), base.spatialMetrics(),
                base.calculationDate());

        assertThat(calculator.calculate(input, AssessmentTestFixtures.riskRule()))
                .isEqualByComparingTo("0.00");
    }
}
