package org.urbansafe.priority.assessment.calculator;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.AssessmentResults.Dimension;
import org.urbansafe.priority.assessment.model.AssessmentResults.Factor;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.AiEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.BusinessEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.InspectionEvidence;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.assessment.rule.RuleAccess;

/** 安全风险与判断置信度 V1 纯计算器。 */
@Component
public class RiskCalculator {

    private final FeedbackSignalCalculator feedbackCalculator;
    private final EvidenceReliabilityEvaluator reliabilityEvaluator;
    private final ConfidenceCalculator confidenceCalculator;

    public RiskCalculator(
            FeedbackSignalCalculator feedbackCalculator,
            EvidenceReliabilityEvaluator reliabilityEvaluator,
            ConfidenceCalculator confidenceCalculator) {
        this.feedbackCalculator = feedbackCalculator;
        this.reliabilityEvaluator = reliabilityEvaluator;
        this.confidenceCalculator = confidenceCalculator;
    }

    public RiskResult calculate(
            BuildingAssessmentInput input,
            RuleSnapshot rule,
            BigDecimal completenessScore) {
        RuleAccess access = new RuleAccess(rule);
        List<Factor> factors = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        BigDecimal base = baseRisk(input, access, factors);
        BigDecimal inspection = inspectionRisk(input, access, factors);
        BigDecimal professional = professionalAndHistoryRisk(input, access, factors, missing);
        BigDecimal spatial = spatialRisk(input, factors, missing);
        BigDecimal feedback = feedbackCalculator.calculate(input, rule);
        if (feedback.compareTo(BigDecimal.ZERO) > 0) {
            factors.add(factor("RESIDENT_FEEDBACK", "近期有效公众反馈形成未核实风险线索",
                    feedback.multiply(access.dimensionWeight("RESIDENT_FEEDBACK")),
                    "INCREASE", "RESIDENT_REPORT", null,
                    access.mapDecimal("/reliability", "PUBLIC_REPORT", "0.35")));
        }
        BigDecimal ai = reviewedAiRisk(input, access, factors);

        List<Dimension> dimensions = List.of(
                dimension(access, "BUILDING_BASE", base, 1),
                dimension(access, "INSPECTION_DEFECT", inspection, input.inspections().size()),
                dimension(access, "PROFESSIONAL_HISTORY", professional, input.businessEvidence().size()),
                dimension(access, "SPATIAL_ENVIRONMENT", spatial, input.spatialMetrics().size()),
                dimension(access, "RESIDENT_FEEDBACK", feedback, input.residentReports().size()),
                dimension(access, "REVIEWED_AI", ai, input.eligibleAiEvidence().size()));

        BigDecimal riskScore = AssessmentMath.output(dimensions.stream()
                .map(Dimension::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal reliabilityScore = reliabilityEvaluator.evaluate(input);
        BigDecimal confidenceScore = confidenceCalculator.calculate(completenessScore, reliabilityScore);

        boolean hasProfessional = input.businessEvidence().stream()
                .anyMatch(item -> "PROFESSIONAL_INSPECTION".equals(item.evidenceType()));
        boolean severeInspection = input.inspections().stream()
                .map(InspectionEvidence::severity)
                .anyMatch(value -> severity(access, value) >= 75);
        BigDecimal manualReviewThreshold = access.decimal(
                "/recommendations/manualReviewRiskThreshold", "50");
        BigDecimal lowConfidenceThreshold = access.decimal(
                "/recommendations/lowConfidenceThreshold", "60");
        BigDecimal professionalInspectionThreshold = access.decimal(
                "/recommendations/professionalInspectionRiskThreshold", "75");
        boolean severeRequiresProfessional = access.bool(
                "/recommendations/severeInspectionRequiresProfessional", true);
        boolean manualReview = riskScore.compareTo(manualReviewThreshold) >= 0
                || confidenceScore.compareTo(lowConfidenceThreshold) < 0;
        boolean professionalInspection = riskScore.compareTo(professionalInspectionThreshold) >= 0
                || (severeRequiresProfessional && severeInspection && !hasProfessional);

        List<String> recommendations = new ArrayList<>();
        if (riskScore.compareTo(manualReviewThreshold) >= 0) {
            recommendations.add("安排人工复核，核对现场病害、历史资料和评分输入");
        }
        if (confidenceScore.compareTo(lowConfidenceThreshold) < 0) {
            recommendations.add("当前判断置信度较低，优先补充资料或开展现场复核");
        }
        if (professionalInspection) {
            recommendations.add("建议委托第三方专业机构开展房屋安全检测");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("保持常态巡检，并在资料变化后重新计算");
        }

        List<Map<String, Object>> excluded = input.excludedAiEvidence().stream()
                .map(item -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sourceType", "AI_INFERENCE");
                    map.put("sourceId", item.inferenceId().toString());
                    map.put("reason", item.exclusionReason());
                    return map;
                })
                .toList();
        for (AiEvidence item : input.excludedAiEvidence()) {
            factors.add(factor("AI_EXCLUDED", "人工智能证据已排除：" + item.exclusionReason(),
                    BigDecimal.ZERO, "EXCLUDED", "AI_INFERENCE",
                    item.inferenceId().toString(), BigDecimal.ZERO));
        }
        if (!hasProfessional) {
            missing.add("有效专业检测资料");
            factors.add(factor("PROFESSIONAL_MISSING", "无有效专业检测：不降低风险分，但降低判断置信度",
                    BigDecimal.ZERO, "NEUTRAL", "PROFESSIONAL_INSPECTION", null, BigDecimal.ZERO));
        }

        List<Factor> orderedFactors = factors.stream()
                .sorted(Comparator.comparing((Factor item) -> item.effect().abs()).reversed()
                        .thenComparing(Factor::factorCode)
                        .thenComparing(item -> item.sourceId() == null ? "" : item.sourceId()))
                .toList();

        return new RiskResult(
                riskScore,
                access.level(riskScore),
                reliabilityScore,
                confidenceScore,
                confidenceCalculator.level(confidenceScore),
                dimensions,
                orderedFactors,
                excluded,
                List.copyOf(missing),
                List.copyOf(recommendations),
                manualReview,
                professionalInspection);
    }

    private BigDecimal baseRisk(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<Factor> factors) {
        var building = input.building();
        BigDecimal age = ageScore(input, access);
        BigDecimal structure = BigDecimal.valueOf(structureScore(access, building.structureType()));
        BigDecimal score = age.multiply(access.decimal("/baseRisk/ageWeight", "0.60"))
                .add(structure.multiply(access.decimal("/baseRisk/structureWeight", "0.40")));
        if (building.illegalModification()) {
            BigDecimal bonus = BigDecimal.valueOf(access.integer("/baseRisk/illegalModificationBonus", 25));
            score = score.add(bonus);
            factors.add(factor("ILLEGAL_MODIFICATION", "楼栋存在违规改造标记",
                    AssessmentMath.output(bonus.multiply(access.dimensionWeight("BUILDING_BASE"))),
                    "INCREASE", "BUILDING", building.buildingId().toString(),
                    access.mapDecimal("/reliability", "BUSINESS_REVIEWED", "0.70")));
        }
        if (building.groundFloorBusiness()
                && booleanAttribute(building.extraAttributes(), "loadChangeClue")) {
            score = score.add(BigDecimal.valueOf(access.integer(
                    "/baseRisk/groundFloorBusinessBonus", 10)));
        }
        factors.add(factor("BUILDING_AGE_STRUCTURE", "楼龄与结构基础风险",
                AssessmentMath.output(score.multiply(access.dimensionWeight("BUILDING_BASE"))),
                "INCREASE", "BUILDING", building.buildingId().toString(),
                access.mapDecimal("/reliability", "BUSINESS_REVIEWED", "0.70")));
        return AssessmentMath.output(score);
    }

    private BigDecimal ageScore(BuildingAssessmentInput input, RuleAccess access) {
        Integer year = input.building().constructionYear();
        boolean missing = year == null || year < 1800 || year > input.calculationDate().getYear();
        JsonNode scores = access.snapshot().ruleContent().path("ageScores");
        if (scores.isArray()) {
            if (missing) {
                for (JsonNode score : scores) {
                    if (score.path("missing").asBoolean(false)) {
                        return BigDecimal.valueOf(score.path("score").asInt(50));
                    }
                }
            } else {
                int age = input.calculationDate().getYear() - year;
                for (JsonNode score : scores) {
                    if (!score.has("maxAge")) continue;
                    if (age <= score.path("maxAge").asInt()) {
                        return BigDecimal.valueOf(score.path("score").asInt());
                    }
                }
            }
        }
        if (missing) return new BigDecimal("50");
        int age = input.calculationDate().getYear() - year;
        return BigDecimal.valueOf(age <= 20 ? 10 : age <= 30 ? 25 : age <= 40 ? 45
                : age <= 50 ? 65 : age <= 70 ? 80 : 95);
    }

    private int structureScore(RuleAccess access, String structure) {
        String normalized = upper(structure);
        String key = switch (normalized) {
            case "剪力墙" -> "SHEAR_WALL";
            case "框架" -> "FRAME";
            case "框架剪力墙", "框剪" -> "FRAME_SHEAR_WALL";
            case "砖混" -> "BRICK_CONCRETE";
            case "砌体" -> "MASONRY";
            case "木结构", "简易结构" -> "WOOD_SIMPLE";
            default -> normalized.isBlank() ? "UNKNOWN" : normalized;
        };
        return access.mapInt("/structureScores", key, access.mapInt("/structureScores", "UNKNOWN", 50));
    }

    private BigDecimal inspectionRisk(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<Factor> factors) {
        if (input.inspections().isEmpty()) return BigDecimal.ZERO.setScale(2);
        int max = input.inspections().stream().mapToInt(item -> severity(access, item.severity())).max().orElse(0);
        long parts = input.inspections().stream()
                .map(InspectionEvidence::inspectionPart)
                .filter(value -> value != null && !value.isBlank())
                .distinct().count();
        int multiPart = parts >= 3 ? 100 : parts == 2 ? 60 : 20;
        boolean worsening = input.inspections().stream().anyMatch(InspectionEvidence::worsening);
        boolean persistent = input.inspections().stream().anyMatch(InspectionEvidence::persistent);
        int persistence = worsening ? 100 : persistent ? 60 : 0;
        BigDecimal score = BigDecimal.valueOf(max)
                .multiply(access.decimal("/inspectionAggregation/maxSeverityWeight", "0.70"))
                .add(BigDecimal.valueOf(multiPart)
                        .multiply(access.decimal("/inspectionAggregation/multiPartWeight", "0.20")))
                .add(BigDecimal.valueOf(persistence)
                        .multiply(access.decimal("/inspectionAggregation/persistenceWeight", "0.10")));
        if (max >= 75) {
            factors.add(factor("SEVERE_INSPECTION", "有效巡检发现严重或危急病害",
                    AssessmentMath.output(score.multiply(access.dimensionWeight("INSPECTION_DEFECT"))),
                    "INCREASE", "INSPECTION_RECORD", null,
                    access.mapDecimal("/reliability", "INSPECTION_COMPLETED", "0.80")));
        }
        return AssessmentMath.output(score);
    }

    private BigDecimal professionalAndHistoryRisk(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<Factor> factors,
            List<String> missing) {
        List<BusinessEvidence> evidence = input.businessEvidence().stream()
                .filter(item -> Set.of("PROFESSIONAL_INSPECTION", "MAINTENANCE_RECORD",
                        "HISTORICAL_COMPLAINT").contains(item.evidenceType()))
                .toList();
        if (evidence.isEmpty()) {
            missing.add("专业和历史证据");
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (BusinessEvidence item : evidence) {
            BigDecimal raw = item.score() != null ? item.score()
                    : BigDecimal.valueOf(severity(access, item.severity()));
            BigDecimal reliability = businessReliability(access, item);
            BigDecimal decay = evidenceDecay(input, item);
            numerator = numerator.add(raw.multiply(reliability).multiply(decay));
            denominator = denominator.add(reliability);
        }
        BigDecimal score = denominator.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : numerator.divide(denominator, 6, RoundingMode.HALF_UP);
        factors.add(factor("PROFESSIONAL_HISTORY", "专业检测、维修和历史证据综合影响",
                AssessmentMath.output(score.multiply(access.dimensionWeight("PROFESSIONAL_HISTORY"))),
                "INCREASE", "BUILDING_EVIDENCE", null,
                access.mapDecimal("/reliability", "BUSINESS_REVIEWED", "0.70")));
        return AssessmentMath.output(score);
    }


    private BigDecimal spatialRisk(
            BuildingAssessmentInput input,
            List<Factor> factors,
            List<String> missing) {
        if (input.spatialMetrics().isEmpty()) {
            missing.add("空间与环境风险指标");
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal score = input.spatialMetrics().stream()
                .map(BuildingAssessmentInput.SpatialMetric::metricValue)
                .filter(java.util.Objects::nonNull)
                .map(AssessmentMath::clamp)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (score.compareTo(BigDecimal.ZERO) > 0) {
            factors.add(factor("SPATIAL_RISK", "空间或环境风险指标",
                    AssessmentMath.output(score.multiply(new BigDecimal("0.10"))),
                    "INCREASE", "SPATIAL_METRIC", null, new BigDecimal("0.60")));
        }
        return AssessmentMath.output(score);
    }

    private BigDecimal reviewedAiRisk(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<Factor> factors) {
        if (input.eligibleAiEvidence().isEmpty()) return BigDecimal.ZERO.setScale(2);
        int maxSeverity = input.eligibleAiEvidence().stream()
                .mapToInt(item -> severity(access, item.severity())).max().orElse(0);
        int quantity = input.eligibleAiEvidence().stream().mapToInt(AiEvidence::quantity).sum();
        int bonusPerExtra = access.integer("/reviewedAi/quantityBonusPerExtraDefect", 5);
        int maxBonus = access.integer("/reviewedAi/maxQuantityBonus", 20);
        BigDecimal score = BigDecimal.valueOf(maxSeverity)
                .add(BigDecimal.valueOf(Math.min(maxBonus, Math.max(0, quantity - 1) * (long) bonusPerExtra)));
        score = AssessmentMath.clamp(score);
        factors.add(factor("REVIEWED_REAL_AI", "经人工确认或修正的真实人工智能病害证据",
                AssessmentMath.output(score.multiply(access.dimensionWeight("REVIEWED_AI"))),
                "INCREASE", "AI_INFERENCE", null,
                access.mapDecimal("/reliability", "AI_REVIEWED_REAL", "0.70")));
        return AssessmentMath.output(score);
    }

    private Dimension dimension(RuleAccess access, String code, BigDecimal score, int evidenceCount) {
        BigDecimal weight = access.dimensionWeight(code);
        return new Dimension(
                code,
                access.dimensionLabel(code),
                AssessmentMath.output(score),
                weight,
                AssessmentMath.output(score.multiply(weight)),
                evidenceCount > 0 ? "AVAILABLE" : "NO_EVIDENCE",
                evidenceCount);
    }

    private Factor factor(
            String code, String label, BigDecimal effect, String direction,
            String sourceType, String sourceId, BigDecimal reliability) {
        return new Factor(code, label, AssessmentMath.output(effect), direction,
                sourceType, sourceId, reliability);
    }

    private BigDecimal businessReliability(RuleAccess access, BusinessEvidence item) {
        String level = upper(item.reliabilityLevel());
        if ("PROFESSIONAL_INSPECTION".equals(item.evidenceType()) && level.contains("VERIFIED")) {
            return access.mapDecimal("/reliability", "PROFESSIONAL_VERIFIED", "1.00");
        }
        if ("MAINTENANCE_RECORD".equals(item.evidenceType()) && level.contains("VERIFIED")) {
            return access.mapDecimal("/reliability", "MAINTENANCE_VERIFIED", "0.85");
        }
        if (level.contains("VERIFIED") || level.contains("REVIEWED")) {
            return access.mapDecimal("/reliability", "BUSINESS_REVIEWED", "0.70");
        }
        return access.mapDecimal("/reliability", "BUSINESS_UNVERIFIED", "0.40");
    }

    private BigDecimal evidenceDecay(BuildingAssessmentInput input, BusinessEvidence item) {
        if (item.occurredAt() == null) return new BigDecimal("0.70");
        long days = Math.max(0, ChronoUnit.DAYS.between(
                item.occurredAt().toLocalDate(), input.calculationDate()));
        if (days <= 365) return BigDecimal.ONE;
        if (days <= 365L * 3) return new BigDecimal("0.85");
        if (days <= 365L * 5) return new BigDecimal("0.70");
        return new BigDecimal("0.50");
    }

    private int severity(RuleAccess access, String value) {
        String normalized = upper(value);
        int fallback = switch (normalized) {
            case "MINOR", "轻微" -> 25;
            case "MODERATE", "一般", "中等" -> 50;
            case "SEVERE", "严重" -> 75;
            case "CRITICAL", "危急" -> 100;
            default -> 0;
        };
        return access.mapInt("/severityScores", normalized.isBlank() ? "NONE" : normalized, fallback);
    }

    private boolean booleanAttribute(Map<String, Object> attributes, String key) {
        if (attributes == null) return false;
        Object value = attributes.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
