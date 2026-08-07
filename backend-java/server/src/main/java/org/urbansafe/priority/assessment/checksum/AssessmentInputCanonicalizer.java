package org.urbansafe.priority.assessment.checksum;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * 将评分输入转换为稳定 JSON。
 *
 * <p>对象键按字典序；数组按元素规范化 JSON 排序；数值固定六位；
 * 字符串使用 Unicode NFC。临时地址、密钥和公众查询凭证会被移除。
 */
@Component
public class AssessmentInputCanonicalizer {

    private static final List<String> SENSITIVE_KEYS = List.of(
            "presignedUrl", "temporaryUrl", "accessKey", "secretKey",
            "trackingSecret", "trackingSecretHash", "authorization", "token");

    private final ObjectMapper objectMapper;

    public AssessmentInputCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalize(Object input) {
        JsonNode normalized = normalize(objectMapper.valueToTree(input));
        try {
            return Normalizer.normalize(objectMapper.writeValueAsString(normalized), Normalizer.Form.NFC);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评分输入规范化失败", ex);
        }
    }

    public JsonNode canonicalTree(Object input) {
        return normalize(objectMapper.valueToTree(input));
    }

    private JsonNode normalize(JsonNode node) {
        if (node == null || node.isNull()) return objectMapper.nullNode();
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> ordered = new TreeMap<>();
            node.fields().forEachRemaining(entry -> {
                if (!SENSITIVE_KEYS.contains(entry.getKey())) {
                    ordered.put(entry.getKey(), normalize(entry.getValue()));
                }
            });
            ordered.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            List<JsonNode> items = new ArrayList<>();
            node.forEach(item -> items.add(normalize(item)));
            items.sort(Comparator.comparing(this::write));
            ArrayNode result = objectMapper.createArrayNode();
            items.forEach(result::add);
            return result;
        }
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue().setScale(6, RoundingMode.HALF_UP);
            return DecimalNode.valueOf(value);
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(
                    Normalizer.normalize(node.textValue(), Normalizer.Form.NFC));
        }
        return node.deepCopy();
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评分数组排序失败", ex);
        }
    }
}
