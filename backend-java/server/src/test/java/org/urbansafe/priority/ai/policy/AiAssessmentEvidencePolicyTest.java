package org.urbansafe.priority.ai.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AiAssessmentEvidencePolicyTest {

    @Test
    void mockResultIsAlwaysDemoOnly() {
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "SUCCEEDED",
                "mode", "MOCK",
                "reviewStatus", "CONFIRMED"));

        assertEquals("DEMO_ONLY", decision.assessmentEligibility());
        assertFalse(decision.eligibleForFormalAssessment());
        assertEquals("SIMULATED", decision.evidenceReliability());
    }

    @Test
    void reviewedActiveRealResultIsEligible() {
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "SUCCEEDED",
                "mode", "REAL",
                "reviewStatus", "CORRECTED",
                "deploymentStage", "ACTIVE",
                "formalEvidenceEnabled", true));

        assertEquals("ELIGIBLE", decision.assessmentEligibility());
        assertTrue(decision.eligibleForFormalAssessment());
        assertEquals("PROFESSIONAL_REVIEWED", decision.evidenceReliability());
    }

    @Test
    void reviewedDemoRealResultRemainsDemoOnly() {
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "SUCCEEDED",
                "mode", "REAL",
                "reviewStatus", "CONFIRMED",
                "deploymentStage", "DEMO",
                "formalEvidenceEnabled", false));

        assertEquals("DEMO_ONLY", decision.assessmentEligibility());
        assertFalse(decision.eligibleForFormalAssessment());
        assertEquals("PROFESSIONAL_REVIEWED", decision.evidenceReliability());
    }

    @Test
    void missingGovernanceFieldsFailClosed() {
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "SUCCEEDED",
                "mode", "REAL",
                "reviewStatus", "CORRECTED"));

        assertEquals("DEMO_ONLY", decision.assessmentEligibility());
        assertFalse(decision.eligibleForFormalAssessment());
    }

    @Test
    void unreviewedRealResultRequiresReview() {
        AiAssessmentEvidencePolicy.Decision decision = AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "SUCCEEDED",
                "mode", "REAL",
                "reviewStatus", "UNREVIEWED"));

        assertEquals("REVIEW_REQUIRED", decision.assessmentEligibility());
        assertFalse(decision.eligibleForFormalAssessment());
    }

    @Test
    void failedOrHumanRejectedResultIsExcluded() {
        assertEquals("EXCLUDED", AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "FAILED", "mode", "REAL", "reviewStatus", "UNREVIEWED"))
                .assessmentEligibility());
        assertEquals("EXCLUDED", AiAssessmentEvidencePolicy.evaluate(Map.of(
                "status", "SUCCEEDED", "mode", "REAL", "reviewStatus", "REJECTED"))
                .assessmentEligibility());
    }
}
