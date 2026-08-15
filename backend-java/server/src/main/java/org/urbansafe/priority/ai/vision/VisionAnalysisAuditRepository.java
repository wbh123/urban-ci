package org.urbansafe.priority.ai.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 将视觉路由与故障转移信息合并到既有 execution_task.inputs JSONB。 */
@Repository
public class VisionAnalysisAuditRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public VisionAnalysisAuditRepository(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(UUID executionTaskId, VisionAnalysisOutcome outcome) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("preferredProvider", outcome.preferredProvider());
        audit.put("actualProvider", outcome.actualProvider());
        audit.put("orchestrationMode", outcome.orchestrationMode());
        audit.put("fallback", outcome.fallback());
        if (outcome.fallbackReason() != null) {
            audit.put("fallbackReason", outcome.fallbackReason());
        }
        if (outcome.difySummary() != null) {
            audit.put("difySummary", outcome.difySummary());
        }
        if (!outcome.difyWarnings().isEmpty()) {
            audit.put("difyWarnings", outcome.difyWarnings());
        }
        jdbc.update("""
                UPDATE ai.execution_task
                SET inputs = COALESCE(inputs, '{}'::jsonb) || CAST(:audit AS jsonb),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """, Map.of(
                "id", executionTaskId,
                "audit", json(audit)));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("视觉分析路由审计信息无法序列化", ex);
        }
    }
}
