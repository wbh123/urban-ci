package org.urbansafe.priority.assessment.rule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.InvalidRequestException;

class AssessmentRuleValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AssessmentRuleValidator validator = new AssessmentRuleValidator();

    @Test
    void rejectsWeightSumThatIsNotOne() throws Exception {
        var content = mapper.readTree("""
                {
                  "dimensions":[
                    {"code":"A","weight":"0.50"},
                    {"code":"B","weight":"0.40"}
                  ],
                  "levels":[{"code":"ALL","min":"0","maxInclusive":"100"}]
                }
                """);

        assertThatThrownBy(() -> validator.validate("RISK", content))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("权重");
    }

    @Test
    void rejectsDuplicatedDimensionCode() throws Exception {
        var content = mapper.readTree("""
                {
                  "dimensions":[
                    {"code":"A","weight":"0.50"},
                    {"code":"A","weight":"0.50"}
                  ],
                  "levels":[{"code":"ALL","min":"0","maxInclusive":"100"}]
                }
                """);

        assertThatThrownBy(() -> validator.validate("RISK", content))
                .isInstanceOf(InvalidRequestException.class);
    }
}
