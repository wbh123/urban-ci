package org.urbansafe.priority.assessment.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class AssessmentResults {

    private AssessmentResults() {
    }

    public record Dimension(
            String code,
            String label,
            BigDecimal score,
            BigDecimal weight,
            BigDecimal contribution,
            String status,
            int evidenceCount) {
    }

    public record Factor(
            String factorCode,
            String label,
            BigDecimal effect,
            String direction,
            String sourceType,
            String sourceId,
            BigDecimal reliability) {
    }

    public record CompletenessResult(
            BigDecimal score,
            String level,
            List<Dimension> dimensions,
            List<String> availableItems,
            List<String> missingItems,
            List<String> suggestions) {
    }

    public record RiskResult(
            BigDecimal riskScore,
            String riskLevel,
            BigDecimal evidenceReliabilityScore,
            BigDecimal confidenceScore,
            String confidenceLevel,
            List<Dimension> dimensions,
            List<Factor> factors,
            List<Map<String, Object>> excludedEvidence,
            List<String> missingData,
            List<String> recommendations,
            boolean needManualReview,
            boolean needProfessionalInspection) {
    }

    public record RenewalResult(
            BigDecimal priorityScore,
            String priorityLevel,
            BigDecimal reliabilityFactor,
            List<Dimension> factors,
            List<String> recommendations) {
    }
}
