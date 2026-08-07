package org.urbansafe.priority.ai.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 人工智能工作流登记查询。 */
@Repository
public class AiWorkflowDefinitionRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiWorkflowDefinitionRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<AiWorkflowDefinition> findByWorkflowOrModelCode(String code) {
        return jdbc.query("""
                SELECT workflow_code, model_code, display_name, provider_code, capability_type,
                       config_key, current_version, input_schema_version, output_schema_version,
                       enabled, quality_status, formal_evidence_enabled, timeout_ms, max_attempts,
                       data_policy
                FROM ai.workflow_definition
                WHERE workflow_code=:code OR model_code=:code
                """, Map.of("code", code), (rs, rowNum) -> new AiWorkflowDefinition(
                rs.getString("workflow_code"),
                rs.getString("model_code"),
                rs.getString("display_name"),
                rs.getString("provider_code"),
                rs.getString("capability_type"),
                rs.getString("config_key"),
                rs.getString("current_version"),
                rs.getString("input_schema_version"),
                rs.getString("output_schema_version"),
                rs.getBoolean("enabled"),
                rs.getString("quality_status"),
                rs.getBoolean("formal_evidence_enabled"),
                rs.getInt("timeout_ms"),
                rs.getInt("max_attempts"),
                parse(rs.getString("data_policy")),
                null, null, false)).stream().findFirst();
    }

    private Map<String, Object> parse(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("工作流数据策略无法解析", ex);
        }
    }
}
