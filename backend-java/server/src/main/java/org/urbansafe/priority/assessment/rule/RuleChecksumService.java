package org.urbansafe.priority.assessment.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.assessment.checksum.AssessmentChecksumService;

/**
 * 规则 JSON 摘要：对象键字典序、数组保持规则顺序、Unicode NFC、UTF-8 SHA-256。
 * 数值保持 JSON 本身表达，权重等需要固定精度的值在规则中使用字符串保存。
 */
@Service
public class RuleChecksumService {

    private final ObjectMapper objectMapper;

    public RuleChecksumService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalize(JsonNode ruleContent) {
        try {
            return Normalizer.normalize(
                    objectMapper.writeValueAsString(sortObjects(ruleContent)),
                    Normalizer.Form.NFC);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("规则 JSON 规范化失败", ex);
        }
    }

    public String checksum(JsonNode ruleContent) {
        return AssessmentChecksumService.sha256(canonicalize(ruleContent));
    }

    private JsonNode sortObjects(JsonNode node) {
        if (node == null || node.isNull()) return objectMapper.nullNode();
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry ->
                    fields.put(entry.getKey(), sortObjects(entry.getValue())));
            fields.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(sortObjects(item)));
            return result;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(
                    Normalizer.normalize(node.textValue(), Normalizer.Form.NFC));
        }
        return node.deepCopy();
    }
}
