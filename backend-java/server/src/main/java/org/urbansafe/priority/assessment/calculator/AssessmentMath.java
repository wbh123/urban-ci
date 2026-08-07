package org.urbansafe.priority.assessment.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class AssessmentMath {

    static final int INTERMEDIATE_SCALE = 6;
    static final int OUTPUT_SCALE = 2;

    private AssessmentMath() {
    }

    static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    static BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.valueOf(100)) > 0) return BigDecimal.valueOf(100);
        return value;
    }

    static BigDecimal intermediate(BigDecimal value) {
        return value.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal output(BigDecimal value) {
        return clamp(value).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal weighted(BigDecimal score, BigDecimal weight) {
        return intermediate(score.multiply(weight));
    }
}
