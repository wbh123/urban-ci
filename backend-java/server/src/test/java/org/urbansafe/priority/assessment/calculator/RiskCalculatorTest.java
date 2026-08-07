package org.urbansafe.priority.assessment.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.assessment.AssessmentTestFixtures;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

class RiskCalculatorTest {

    private final RiskCalculator calculator = new RiskCalculator(
            new FeedbackSignalCalculator(),
            new EvidenceReliabilityEvaluator(),
            new ConfidenceCalculator());

    @Test
    void severeEvidenceCanBeHighRiskAndLowConfidenceAtSameTime() throws Exception {
        var result = calculator.calculate(
                AssessmentTestFixtures.lowCompletenessSevereInput(),
                AssessmentTestFixtures.riskRule(),
                new java.math.BigDecimal("20.00"));

        assertThat(result.riskScore()).isGreaterThanOrEqualTo(new java.math.BigDecimal("50"));
        assertThat(result.confidenceScore()).isLessThan(new java.math.BigDecimal("60"));
        assertThat(result.needManualReview()).isTrue();
        assertThat(result.needProfessionalInspection()).isTrue();
    }

    @Test
    void mockAiIsExcludedFromFormalRiskContribution() throws Exception {
        var result = calculator.calculate(
                AssessmentTestFixtures.lowCompletenessSevereInput(),
                AssessmentTestFixtures.riskRule(),
                new java.math.BigDecimal("20.00"));

        assertThat(result.dimensions().stream()
                .filter(item -> "REVIEWED_AI".equals(item.code()))
                .findFirst().orElseThrow().score())
                .isEqualByComparingTo("0.00");
        assertThat(result.excludedEvidence()).hasSize(1);
    }


    @Test
    void riskFormulaUsesRuleSeverityScores() throws Exception {
        var input = AssessmentTestFixtures.fullInput();
        var defaultResult = calculator.calculate(input, AssessmentTestFixtures.riskRule(), new BigDecimal("90.00"));

        var content = (ObjectNode) AssessmentTestFixtures.riskRule().ruleContent().deepCopy();
        var severityScores = content.putObject("severityScores");
        severityScores.put("NONE", 0);
        severityScores.put("MINOR", 25);
        severityScores.put("MODERATE", 50);
        severityScores.put("SEVERE", 10);
        severityScores.put("CRITICAL", 100);
        var mutedRule = new RuleSnapshot(UUID.randomUUID(), "RISK", "RISK-MUTED", "RISK-MUTED",
                content, "checksum", "ACTIVE", OffsetDateTime.now(ZoneOffset.UTC));
        var mutedResult = calculator.calculate(input, mutedRule, new BigDecimal("90.00"));

        assertThat(defaultResult.riskScore()).isGreaterThan(mutedResult.riskScore());
    }

    @Test
    void riskThresholdsUseDocumentedBoundaries() throws Exception {
        var rule = AssessmentTestFixtures.riskRule();
        var access = new org.urbansafe.priority.assessment.rule.RuleAccess(rule);
        assertThat(access.level(new java.math.BigDecimal("24.99"))).isEqualTo("LOW");
        assertThat(access.level(new java.math.BigDecimal("25.00"))).isEqualTo("MEDIUM");
        assertThat(access.level(new java.math.BigDecimal("50.00"))).isEqualTo("HIGH");
        assertThat(access.level(new java.math.BigDecimal("75.00"))).isEqualTo("VERY_HIGH");
    }
}
