package org.urbansafe.priority.assessment.service;

import org.springframework.stereotype.Service;
import org.urbansafe.priority.assessment.calculator.CompletenessCalculator;
import org.urbansafe.priority.assessment.model.AssessmentResults.CompletenessResult;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

@Service
public class CompletenessAssessmentService {

    private final CompletenessCalculator calculator;
    private final CompletenessExplanationBuilder explanationBuilder;

    public CompletenessAssessmentService(
            CompletenessCalculator calculator,
            CompletenessExplanationBuilder explanationBuilder) {
        this.calculator = calculator;
        this.explanationBuilder = explanationBuilder;
    }

    public CompletenessResult calculate(BuildingAssessmentInput input, RuleSnapshot rule) {
        return explanationBuilder.build(calculator.calculate(input, rule));
    }
}
