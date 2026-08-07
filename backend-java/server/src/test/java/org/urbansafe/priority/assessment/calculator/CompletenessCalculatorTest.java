package org.urbansafe.priority.assessment.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.urbansafe.priority.assessment.AssessmentTestFixtures;

class CompletenessCalculatorTest {

    private final CompletenessCalculator calculator = new CompletenessCalculator();

    @Test
    void fullEvidenceProducesOneHundred() throws Exception {
        var result = calculator.calculate(
                AssessmentTestFixtures.fullInput(),
                AssessmentTestFixtures.completenessRule());

        assertThat(result.score()).isEqualByComparingTo("100.00");
        assertThat(result.level()).isEqualTo("EXCELLENT");
        assertThat(result.missingItems()).isEmpty();
    }

    @Test
    void lowCompletenessDoesNotPretendDataIsComplete() throws Exception {
        var result = calculator.calculate(
                AssessmentTestFixtures.lowCompletenessSevereInput(),
                AssessmentTestFixtures.completenessRule());

        assertThat(result.score()).isLessThan(new java.math.BigDecimal("50"));
        assertThat(result.missingItems()).contains("有效专业检测资料");
    }
}
