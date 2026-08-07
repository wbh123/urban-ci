package org.urbansafe.priority.assessment.rule;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.common.exception.InvalidRequestException;

/** 以稳定方式读取已校验规则快照。 */
public final class RuleAccess {

    private final RuleSnapshot rule;

    public RuleAccess(RuleSnapshot rule) {
        this.rule = rule;
    }

    public RuleSnapshot snapshot() {
        return rule;
    }

    public BigDecimal dimensionWeight(String code) {
        for (JsonNode node : rule.ruleContent().path("dimensions")) {
            if (code.equals(node.path("code").asText())) {
                return decimal(node.path("weight"));
            }
        }
        throw invalid("RULE_DIMENSION_MISSING", "规则缺少维度: " + code);
    }

    public String dimensionLabel(String code) {
        for (JsonNode node : rule.ruleContent().path("dimensions")) {
            if (code.equals(node.path("code").asText())) {
                return node.path("label").asText(code);
            }
        }
        return code;
    }

    public BigDecimal decimal(String pointer, String fallback) {
        JsonNode node = rule.ruleContent().at(pointer);
        return node.isMissingNode() || node.isNull() ? new BigDecimal(fallback) : decimal(node);
    }

    public int integer(String pointer, int fallback) {
        JsonNode node = rule.ruleContent().at(pointer);
        return node.isMissingNode() || node.isNull() ? fallback : node.asInt(fallback);
    }

    public boolean bool(String pointer, boolean fallback) {
        JsonNode node = rule.ruleContent().at(pointer);
        return node.isMissingNode() || node.isNull() ? fallback : node.asBoolean(fallback);
    }

    public BigDecimal mapDecimal(String pointer, String key, String fallback) {
        JsonNode node = rule.ruleContent().at(pointer).path(key);
        return node.isMissingNode() || node.isNull() ? new BigDecimal(fallback) : decimal(node);
    }

    public int mapInt(String pointer, String key, int fallback) {
        JsonNode node = rule.ruleContent().at(pointer).path(key);
        return node.isMissingNode() || node.isNull() ? fallback : node.asInt(fallback);
    }

    public String level(BigDecimal score) {
        for (JsonNode level : rule.ruleContent().path("levels")) {
            BigDecimal min = decimal(level.path("min"));
            if (score.compareTo(min) < 0) continue;
            if (level.has("maxExclusive")
                    && score.compareTo(decimal(level.path("maxExclusive"))) < 0) {
                return level.path("code").asText();
            }
            if (level.has("maxInclusive")
                    && score.compareTo(decimal(level.path("maxInclusive"))) <= 0) {
                return level.path("code").asText();
            }
        }
        throw invalid("RULE_LEVEL_NOT_MATCHED", "分数未匹配任何等级: " + score);
    }

    public Map<String, Integer> integerMap(String pointer) {
        JsonNode node = rule.ruleContent().at(pointer);
        java.util.LinkedHashMap<String, Integer> result = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        fields.forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asInt()));
        return result;
    }

    private BigDecimal decimal(JsonNode node) {
        try {
            return new BigDecimal(node.asText());
        } catch (RuntimeException ex) {
            throw invalid("RULE_NUMBER_INVALID", "规则数字无效: " + node);
        }
    }

    private InvalidRequestException invalid(String code, String message) {
        return new InvalidRequestException(code, message);
    }
}
