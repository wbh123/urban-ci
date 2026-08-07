package org.urbansafe.priority.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 将任意审计快照转换为 JSON，并递归过滤口令、令牌、密钥、手机号和邮箱。
 */
@Component
public class AuditDataSanitizer {

    /** 必须完全移除内容的敏感字段，比较时忽略大小写。 */
    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "passwordhash", "accesstoken", "refreshtoken",
            "authorization", "jwtsecret", "amapkey");

    /** 需要保留“存在”语义但隐藏原值的个人信息字段。 */
    private static final Set<String> MASKED_FIELDS = Set.of("phone", "email");

    private final ObjectMapper objectMapper;

    /**
     * 创建审计脱敏器。
     *
     * @param objectMapper 项目统一 Jackson 映射器
     */
    public AuditDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 把输入转换并递归脱敏；空输入返回空对象，转换失败会显式抛出异常以触发业务事务回滚。
     *
     * @param value 业务快照或详情
     * @return 可安全持久化的 JSON 节点
     */
    public JsonNode sanitize(Object value) {
        JsonNode source = value == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(value);
        return sanitizeNode(source);
    }

    /**
     * 递归复制并脱敏 JSON 节点，避免修改调用方持有的原节点。
     *
     * @param source 原始节点
     * @return 脱敏后的新节点
     */
    private JsonNode sanitizeNode(JsonNode source) {
        if (source == null || source.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (source.isArray()) {
            ArrayNode sanitizedArray = objectMapper.createArrayNode();
            source.forEach(child -> sanitizedArray.add(sanitizeNode(child)));
            return sanitizedArray;
        }
        if (!source.isObject()) {
            return source.deepCopy();
        }

        ObjectNode sanitizedObject = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalizedName = field.getKey().replace("_", "").toLowerCase(Locale.ROOT);
            if (REDACTED_FIELDS.contains(normalizedName)) {
                sanitizedObject.put(field.getKey(), "[REDACTED]");
            } else if (MASKED_FIELDS.contains(normalizedName)) {
                sanitizedObject.put(field.getKey(), "[MASKED]");
            } else {
                sanitizedObject.set(field.getKey(), sanitizeNode(field.getValue()));
            }
        }
        return sanitizedObject;
    }
}
