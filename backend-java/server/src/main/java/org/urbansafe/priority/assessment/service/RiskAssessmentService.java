package org.urbansafe.priority.assessment.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.assessment.calculator.RiskCalculator;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

@Service
public class RiskAssessmentService {

    private final RiskCalculator calculator;
    private final RiskExplanationBuilder explanationBuilder;

    public RiskAssessmentService(
            RiskCalculator calculator,
            RiskExplanationBuilder explanationBuilder) {
        this.calculator = calculator;
        this.explanationBuilder = explanationBuilder;
    }

    public RiskResult calculate(
            BuildingAssessmentInput input, RuleSnapshot rule, BigDecimal completenessScore) {
        return explanationBuilder.build(calculator.calculate(input, rule, completenessScore));
    }
}
