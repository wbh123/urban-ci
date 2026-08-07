package org.urbansafe.priority.assessment.calculator;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** 判断置信度 V1：70% 完整度 + 30% 实际证据可靠性。 */
@Component
public class ConfidenceCalculator {

    public BigDecimal calculate(BigDecimal completenessScore, BigDecimal evidenceReliabilityScore) {
        return AssessmentMath.output(
                completenessScore.multiply(new BigDecimal("0.70"))
                        .add(evidenceReliabilityScore.multiply(new BigDecimal("0.30"))));
    }

    public String level(BigDecimal score) {
        if (score.compareTo(new BigDecimal("80")) >= 0) return "HIGH";
        if (score.compareTo(new BigDecimal("60")) >= 0) return "MEDIUM";
        return "LOW";
    }
}
