package org.urbansafe.priority.assessment.calculator;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.AssessmentResults.Dimension;
import org.urbansafe.priority.assessment.model.AssessmentResults.RenewalResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.assessment.rule.RuleAccess;

/** 城市更新优先级 V1 纯计算器。风险分和优先级分保持独立。 */
@Component
public class RenewalPriorityCalculator {

    private final FeedbackSignalCalculator feedbackSignalCalculator;

    public RenewalPriorityCalculator(FeedbackSignalCalculator feedbackSignalCalculator) {
        this.feedbackSignalCalculator = feedbackSignalCalculator;
    }

    public RenewalResult calculate(
            BuildingAssessmentInput input,
            RiskResult risk,
            RuleSnapshot renewalRule,
            RuleSnapshot riskRule) {
        RuleAccess access = new RuleAccess(renewalRule);
        BigDecimal population = populationImpact(input, access);
        BigDecimal age = buildingAge(input, access);
        BigDecimal publicValue = evidenceScore(input, "PUBLIC_VALUE");
        BigDecimal feedback = feedbackSignalCalculator.calculate(input, riskRule);
        BigDecimal governance = governanceUrgency(input);

        List<Dimension> factors = List.of(
                dimension(access, "RISK", risk.riskScore()),
                dimension(access, "POPULATION_IMPACT", population),
                dimension(access, "BUILDING_AGE", age),
                dimension(access, "PUBLIC_VALUE", publicValue),
                dimension(access, "FEEDBACK_URGENCY", feedback),
                dimension(access, "GOVERNANCE_URGENCY", governance));

        BigDecimal base = factors.stream()
                .map(Dimension::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reliabilityFactor = access.decimal("/reliabilityFactor/base", "0.85")
                .add(access.decimal("/reliabilityFactor/confidenceWeight", "0.15")
                        .multiply(risk.confidenceScore())
                        .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal priority = AssessmentMath.output(base.multiply(reliabilityFactor));

        List<String> recommendations = new ArrayList<>();
        String level = access.level(priority);
        if ("P1".equals(level)) {
            recommendations.add("优先纳入治理或城市更新评估，并安排跨部门复核");
        } else if ("P2".equals(level)) {
            recommendations.add("纳入近期治理储备，补齐证据后复核实施顺序");
        } else {
            recommendations.add("保持巡检和资料更新，按排名变化动态评估");
        }
        if (risk.confidenceScore().compareTo(new BigDecimal("60")) < 0) {
            recommendations.add("优先级已按低置信度折减，不能以资料不足推断楼栋安全");
        }
        return new RenewalResult(
                priority,
                level,
                reliabilityFactor.setScale(6, RoundingMode.HALF_UP),
                factors,
                List.copyOf(recommendations));
    }

    private BigDecimal populationImpact(BuildingAssessmentInput input, RuleAccess access) {
        int residents = nonNegative(input.building().residentCount());
        int vulnerable = nonNegative(input.building().elderlyCount())
                + nonNegative(input.building().childCount());
        int residentScore = residentScore(access, residents);
        int vulnerableFullScoreCount = Math.max(1, access.integer(
                "/populationImpact/vulnerableFullScoreCount", 200));
        BigDecimal vulnerableScore = BigDecimal.valueOf(
                Math.min(100, vulnerable * 100.0 / vulnerableFullScoreCount));
        return AssessmentMath.output(
                BigDecimal.valueOf(residentScore)
                        .multiply(access.decimal("/populationImpact/residentWeight", "0.70"))
                        .add(vulnerableScore
                                .multiply(access.decimal("/populationImpact/vulnerableWeight", "0.30"))));
    }

    private int residentScore(RuleAccess access, int residents) {
        JsonNode scores = access.snapshot().ruleContent().at("/populationImpact/residentScores");
        if (scores.isArray()) {
            for (JsonNode score : scores) {
                if (residents <= score.path("max").asInt(Integer.MAX_VALUE)) {
                    return score.path("score").asInt(0);
                }
            }
        }
        return residents == 0 ? 0 : residents < 100 ? 20 : residents < 300 ? 40
                : residents < 600 ? 60 : residents < 1000 ? 80 : 100;
    }

    private BigDecimal buildingAge(BuildingAssessmentInput input, RuleAccess access) {
        Integer constructionYear = input.building().constructionYear();
        boolean missing = constructionYear == null || constructionYear > input.calculationDate().getYear();
        JsonNode scores = access.snapshot().ruleContent().at("/buildingAge/ageScores");
        if (!scores.isArray()) scores = access.snapshot().ruleContent().path("buildingAgeScores");
        if (scores.isArray()) {
            if (missing) {
                for (JsonNode score : scores) {
                    if (score.path("missing").asBoolean(false)) {
                        return BigDecimal.valueOf(score.path("score").asInt(50))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                }
            } else {
                int age = Math.max(0, input.calculationDate().getYear() - constructionYear);
                for (JsonNode score : scores) {
                    if (!score.has("maxAge")) continue;
                    if (age <= score.path("maxAge").asInt()) {
                        return BigDecimal.valueOf(score.path("score").asInt())
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
        }
        if (missing) return new BigDecimal("50.00");
        int age = Math.max(0, input.calculationDate().getYear() - constructionYear);
        return BigDecimal.valueOf(Math.min(100, age * 100.0 / 70.0)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal governanceUrgency(BuildingAssessmentInput input) {
        BigDecimal score = evidenceScore(input, "GOVERNANCE_URGENCY");
        if (input.building().illegalModification()) score = score.max(new BigDecimal("70"));
        if (input.building().groundFloorBusiness()) score = score.max(new BigDecimal("40"));
        return AssessmentMath.output(score);
    }

    private BigDecimal evidenceScore(BuildingAssessmentInput input, String evidenceType) {
        return input.businessEvidence().stream()
                .filter(item -> evidenceType.equals(item.evidenceType()))
                .map(item -> item.score() != null ? item.score() : structuredScore(item.evidenceData(), "score"))
                .filter(java.util.Objects::nonNull)
                .map(AssessmentMath::clamp)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    private BigDecimal structuredScore(Map<String, Object> attributes, String key) {
        if (attributes == null || attributes.get(key) == null) return null;
        try {
            return AssessmentMath.output(new BigDecimal(String.valueOf(attributes.get(key))));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Dimension dimension(RuleAccess access, String code, BigDecimal score) {
        BigDecimal weight = access.dimensionWeight(code);
        return new Dimension(code, access.dimensionLabel(code), AssessmentMath.output(score), weight,
                AssessmentMath.output(score.multiply(weight)), "AVAILABLE", 1);
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
