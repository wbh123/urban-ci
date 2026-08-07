package org.urbansafe.priority.assessment.rule;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.common.exception.InvalidRequestException;

/** 第四阶段规则草稿的服务端校验器。 */
@Component
public class AssessmentRuleValidator {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal EPSILON = new BigDecimal("0.000001");
    private static final List<String> RENEWAL_RANKING_KEYS = List.of(
            "priorityScore DESC",
            "riskScore DESC",
            "confidenceScore DESC",
            "residentCount DESC",
            "buildingCode ASC",
            "buildingId ASC");
    private static final Set<String> RISK_SEVERITY_KEYS = Set.of(
            "NONE", "MINOR", "MODERATE", "SEVERE", "CRITICAL");

    public void validate(String ruleType, JsonNode content) {
        if (content == null || !content.isObject()) {
            invalid("RULE_CONTENT_INVALID", "规则内容必须是 JSON 对象");
        }
        if (!Set.of("COMPLETENESS", "RISK", "RENEWAL").contains(ruleType)) {
            invalid("RULE_TYPE_INVALID", "不支持的规则类型: " + ruleType);
        }
        JsonNode dimensions = content.path("dimensions");
        if (!dimensions.isArray() || dimensions.isEmpty()) {
            invalid("RULE_DIMENSIONS_REQUIRED", "规则至少包含一个评分维度");
        }
        Set<String> codes = new HashSet<>();
        BigDecimal weightSum = BigDecimal.ZERO;
        for (JsonNode dimension : dimensions) {
            String code = dimension.path("code").asText("");
            if (code.isBlank() || !codes.add(code)) {
                invalid("RULE_DIMENSION_CODE_DUPLICATED", "评分维度代码不能为空或重复: " + code);
            }
            BigDecimal weight = decimal(dimension.path("weight"), "维度权重");
            if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(ONE) > 0) {
                invalid("RULE_WEIGHT_OUT_OF_RANGE", "维度权重必须位于 0 到 1");
            }
            weightSum = weightSum.add(weight);
        }
        if (weightSum.subtract(ONE).abs().compareTo(EPSILON) > 0) {
            invalid("RULE_WEIGHT_SUM_INVALID", "维度权重之和必须为 1，当前为 " + weightSum);
        }
        validateLevels(content.path("levels"));
        validateScores(content);
        switch (ruleType) {
            case "COMPLETENESS" -> validateCompletenessRule(content);
            case "RISK" -> validateRiskRule(content);
            case "RENEWAL" -> validateRenewalRule(content);
            default -> invalid("RULE_TYPE_INVALID", "不支持的规则类型: " + ruleType);
        }
    }

    private void validateCompletenessRule(JsonNode content) {
        requireObject(content, "fieldWeights");
        requireArray(content, "inspectionRecency");
        requireArray(content, "imageCoverage");
        requireObject(content, "evidenceCoverage");
        JsonNode recency = requireObject(content, "evidenceRecency");
        requireNumber(recency, "maintenanceYears", "evidenceRecency.maintenanceYears");
        requireNumber(recency, "professionalInspectionYears", "evidenceRecency.professionalInspectionYears");
        validateIncreasingWindows(content.path("inspectionRecency"), "maxDays", "inspectionRecency");
        validateImageThresholds(content.path("imageCoverage"));
        if (!containsFallbackScore(content.path("inspectionRecency"))) {
            invalid("RULE_COMPLETENESS_FALLBACK_REQUIRED", "inspectionRecency 必须包含 fallbackScore");
        }
    }

    private void validateRiskRule(JsonNode content) {
        JsonNode baseRisk = requireObject(content, "baseRisk");
        requireArray(content, "ageScores");
        requireObject(content, "structureScores");
        JsonNode severityScores = requireObject(content, "severityScores");
        JsonNode inspectionAggregation = requireObject(content, "inspectionAggregation");
        requireObject(content, "reliability");
        JsonNode feedback = requireObject(content, "feedback");
        requireObject(content, "reviewedAi");
        JsonNode confidence = requireObject(content, "confidence");
        JsonNode recommendations = requireObject(content, "recommendations");

        validateWeightSum(baseRisk, "baseRisk", "ageWeight", "structureWeight");
        requireKeys(severityScores, "severityScores", RISK_SEVERITY_KEYS);
        validateWeightSum(inspectionAggregation, "inspectionAggregation",
                "maxSeverityWeight", "multiPartWeight", "persistenceWeight");
        validateWeightSum(confidence, "confidence",
                "completenessWeight", "evidenceReliabilityWeight");
        validateScoreRange(confidence, "lowThreshold", "confidence.lowThreshold");
        validateScoreRange(recommendations, "manualReviewRiskThreshold",
                "recommendations.manualReviewRiskThreshold");
        validateScoreRange(recommendations, "lowConfidenceThreshold",
                "recommendations.lowConfidenceThreshold");
        validateScoreRange(recommendations, "professionalInspectionRiskThreshold",
                "recommendations.professionalInspectionRiskThreshold");
        validateIncreasingWindows(requireArray(feedback, "timeDecay"), "maxDays", "feedback.timeDecay");
    }

    private void validateRenewalRule(JsonNode content) {
        requireObject(content, "populationImpact");
        requireObject(content, "buildingAge");
        requireObject(content, "reliabilityFactor");
        JsonNode rankingKeys = requireArray(content, "rankingKeys");
        List<String> normalizedKeys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode keyNode : rankingKeys) {
            String key = normalizeRankingKey(keyNode.asText(""));
            if (!RENEWAL_RANKING_KEYS.contains(key) || !seen.add(key)) {
                invalid("RULE_RENEWAL_RANKING_KEYS_INVALID",
                        "rankingKeys 包含不支持或重复的排序键: " + keyNode.asText(""));
            }
            normalizedKeys.add(key);
        }
        if (!normalizedKeys.equals(RENEWAL_RANKING_KEYS)) {
            invalid("RULE_RENEWAL_RANKING_KEYS_INVALID",
                    "rankingKeys 必须完整且按固定顺序声明六级稳定排序键");
        }
    }

    private void validateLevels(JsonNode levels) {
        if (!levels.isArray() || levels.isEmpty()) {
            invalid("RULE_LEVELS_REQUIRED", "规则必须定义连续等级区间");
        }
        BigDecimal expectedMin = BigDecimal.ZERO;
        Set<String> codes = new HashSet<>();
        for (JsonNode level : levels) {
            String code = level.path("code").asText("");
            if (code.isBlank() || !codes.add(code)) {
                invalid("RULE_LEVEL_CODE_DUPLICATED", "等级代码不能为空或重复: " + code);
            }
            BigDecimal min = decimal(level.path("min"), "等级最小值");
            if (min.compareTo(expectedMin) != 0) {
                invalid("RULE_LEVEL_RANGE_INVALID", "等级区间存在空洞或重叠，期望起点 " + expectedMin);
            }
            JsonNode maxExclusive = level.get("maxExclusive");
            JsonNode maxInclusive = level.get("maxInclusive");
            if (maxExclusive != null) {
                expectedMin = decimal(maxExclusive, "等级上限");
            } else if (maxInclusive != null) {
                BigDecimal max = decimal(maxInclusive, "等级上限");
                if (max.compareTo(BigDecimal.valueOf(100)) != 0) {
                    invalid("RULE_LEVEL_RANGE_INVALID", "最后等级上限必须为 100");
                }
                expectedMin = max;
            } else {
                invalid("RULE_LEVEL_RANGE_INVALID", "等级缺少上限");
            }
        }
        if (expectedMin.compareTo(BigDecimal.valueOf(100)) != 0) {
            invalid("RULE_LEVEL_RANGE_INVALID", "等级区间必须完整覆盖 0 到 100");
        }
    }

    private void validateScores(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase();
                JsonNode value = entry.getValue();
                if ((key.equals("score") || key.endsWith("score")) && value.isNumber()) {
                    BigDecimal score = value.decimalValue();
                    if (score.compareTo(BigDecimal.ZERO) < 0
                            || score.compareTo(BigDecimal.valueOf(100)) > 0) {
                        invalid("RULE_SCORE_OUT_OF_RANGE", "规则映射分必须位于 0 到 100");
                    }
                }
                if ((key.endsWith("days") || key.endsWith("years")) && value.isNumber()
                        && value.decimalValue().compareTo(BigDecimal.ZERO) < 0) {
                    invalid("RULE_TIME_WINDOW_INVALID", "时间窗口不能为负数");
                }
                validateScores(value);
            });
        } else if (node.isArray()) {
            node.forEach(this::validateScores);
        }
    }

    private void validateIncreasingWindows(JsonNode nodes, String field, String label) {
        long previous = -1;
        for (JsonNode node : nodes) {
            if (!node.has(field)) continue;
            long current = node.path(field).asLong(-1);
            if (current <= previous) {
                invalid("RULE_TIME_WINDOW_INVALID", label + " 的 " + field + " 必须递增");
            }
            previous = current;
        }
    }

    private void validateImageThresholds(JsonNode nodes) {
        int previousImages = -1;
        int previousParts = -1;
        boolean hasZeroFallback = false;
        for (JsonNode node : nodes) {
            requireNumber(node, "minImages", "imageCoverage.minImages");
            requireNumber(node, "minParts", "imageCoverage.minParts");
            requireNumber(node, "score", "imageCoverage.score");
            int images = node.path("minImages").asInt(-1);
            int parts = node.path("minParts").asInt(-1);
            if (images < previousImages || parts < previousParts) {
                invalid("RULE_COMPLETENESS_IMAGE_THRESHOLDS_INVALID",
                        "imageCoverage 的图片和部位阈值必须递增");
            }
            if (images == 0 && parts == 0) {
                hasZeroFallback = true;
            }
            previousImages = images;
            previousParts = parts;
        }
        if (!hasZeroFallback) {
            invalid("RULE_COMPLETENESS_FALLBACK_REQUIRED", "imageCoverage 必须包含 0 图片 0 部位的兜底项");
        }
    }

    private boolean containsFallbackScore(JsonNode nodes) {
        for (JsonNode node : nodes) {
            if (node.has("fallbackScore")) return true;
        }
        return false;
    }

    private void requireKeys(JsonNode object, String label, Set<String> requiredKeys) {
        Set<String> actual = new HashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!actual.containsAll(requiredKeys)) {
            Set<String> missing = new HashSet<>(requiredKeys);
            missing.removeAll(actual);
            invalid("RULE_FIELD_REQUIRED", label + " 缺少必需字段: " + missing);
        }
    }

    private void validateWeightSum(JsonNode object, String label, String... fields) {
        BigDecimal sum = BigDecimal.ZERO;
        for (String field : fields) {
            requireNumber(object, field, label + "." + field);
            BigDecimal value = decimal(object.path(field), label + "." + field);
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE) > 0) {
                invalid("RULE_WEIGHT_OUT_OF_RANGE", label + "." + field + " 必须位于 0 到 1");
            }
            sum = sum.add(value);
        }
        if (sum.subtract(ONE).abs().compareTo(EPSILON) > 0) {
            invalid("RULE_WEIGHT_SUM_INVALID", label + " 权重之和必须为 1，当前为 " + sum);
        }
    }

    private void validateScoreRange(JsonNode object, String field, String label) {
        requireNumber(object, field, label);
        BigDecimal value = decimal(object.path(field), label);
        if (value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            invalid("RULE_SCORE_OUT_OF_RANGE", label + " 必须位于 0 到 100");
        }
    }

    private JsonNode requireObject(JsonNode content, String field) {
        JsonNode value = content.path(field);
        if (!value.isObject() || value.isEmpty()) {
            invalid("RULE_FIELD_REQUIRED", field + " 必须是非空对象");
        }
        return value;
    }

    private JsonNode requireArray(JsonNode content, String field) {
        JsonNode value = content.path(field);
        if (!value.isArray() || value.isEmpty()) {
            invalid("RULE_FIELD_REQUIRED", field + " 必须是非空数组");
        }
        return value;
    }

    private void requireNumber(JsonNode content, String field, String label) {
        JsonNode value = content.path(field);
        if (!value.isNumber() && !value.isTextual()) {
            invalid("RULE_NUMBER_INVALID", label + " 必须是数字");
        }
        decimal(value, label);
    }

    private String normalizeRankingKey(String value) {
        String normalized = value == null ? "" : value.trim().replace(':', ' ');
        String[] parts = normalized.split("\\s+");
        if (parts.length != 2) return normalized;
        return parts[0] + " " + parts[1].toUpperCase();
    }

    private BigDecimal decimal(JsonNode node, String label) {
        try {
            return new BigDecimal(node.asText());
        } catch (RuntimeException ex) {
            invalid("RULE_NUMBER_INVALID", label + "必须是数字");
            return BigDecimal.ZERO;
        }
    }

    private void invalid(String code, String message) {
        throw new InvalidRequestException(code, message);
    }
}
