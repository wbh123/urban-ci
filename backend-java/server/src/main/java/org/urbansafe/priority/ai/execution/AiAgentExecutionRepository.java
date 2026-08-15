package org.urbansafe.priority.ai.execution;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Spring AI 智能编排执行轨迹持久层。 */
@Repository
public class AiAgentExecutionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AiAgentExecutionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void create(AiAgentExecution execution) {
        jdbc.update("""
                INSERT INTO ai.agent_execution (
                    id, business_type, business_id, question, status,
                    requested_by, requested_by_name, model_code, started_at)
                VALUES (:id, :businessType, :businessId, :question, :status,
                        :requestedBy, :requestedByName, :modelCode, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", execution.id())
                .addValue("businessType", execution.businessType())
                .addValue("businessId", execution.businessId())
                .addValue("question", execution.question())
                .addValue("status", execution.status().name())
                .addValue("requestedBy", execution.requestedBy())
                .addValue("requestedByName", execution.requestedByName())
                .addValue("modelCode", execution.modelCode()));
    }

    @Transactional
    public void appendSteps(UUID executionId, List<AiAgentExecutionStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        for (AiAgentExecutionStep step : steps) {
            jdbc.update("""
                    INSERT INTO ai.agent_execution_step (
                        execution_id, seq_no, type, tool_name, provider,
                        status, duration_ms, error_code, detail, created_at)
                    VALUES (:executionId, :seqNo, :type, :toolName, :provider,
                            :status, :durationMs, :errorCode, :detail, CURRENT_TIMESTAMP)
                    """, new MapSqlParameterSource()
                    .addValue("executionId", executionId)
                    .addValue("seqNo", step.seqNo())
                    .addValue("type", step.type().name())
                    .addValue("toolName", step.toolName())
                    .addValue("provider", step.provider())
                    .addValue("status", step.status().name())
                    .addValue("durationMs", step.durationMs())
                    .addValue("errorCode", step.errorCode())
                    .addValue("detail", step.detail()));
        }
    }

    @Transactional
    public void complete(
            UUID executionId,
            AiAgentExecutionStatus status,
            Long durationMs,
            String summary,
            String modelCode,
            String errorCode,
            String errorMessage) {
        jdbc.update("""
                UPDATE ai.agent_execution
                SET status=:status,
                    finished_at=CURRENT_TIMESTAMP,
                    duration_ms=:durationMs,
                    summary=:summary,
                    model_code=:modelCode,
                    error_code=:errorCode,
                    error_message=:errorMessage,
                    version=version+1
                WHERE id=:executionId
                """, new MapSqlParameterSource()
                .addValue("executionId", executionId)
                .addValue("status", status.name())
                .addValue("durationMs", durationMs)
                .addValue("summary", summary)
                .addValue("modelCode", modelCode)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", errorMessage));
    }

    public Optional<AiAgentExecution> findById(UUID executionId) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT * FROM ai.agent_execution WHERE id=:id",
                new MapSqlParameterSource("id", executionId),
                rowMapper);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        AiAgentExecution execution = new AiAgentExecution(
                executionId,
                stringValue(row.get("business_type")),
                uuidValue(row.get("business_id")),
                stringValue(row.get("question")),
                uuidValue(row.get("requested_by")),
                stringValue(row.get("requested_by_name")));
        execution.setStatus(AiAgentExecutionStatus.valueOf(stringValue(row.get("status"))));
        execution.setModelCode(stringValue(row.get("model_code")));
        execution.setStartedAt(instantValue(row.get("started_at")));
        execution.setFinishedAt(instantValue(row.get("finished_at")));
        execution.setSummary(stringValue(row.get("summary")));
        execution.setErrorCode(stringValue(row.get("error_code")));
        execution.setErrorMessage(stringValue(row.get("error_message")));
        if (row.get("duration_ms") != null) {
            execution.setDurationMs(((Number) row.get("duration_ms")).longValue());
        }
        List<Map<String, Object>> stepRows = jdbc.query(
                """
                SELECT * FROM ai.agent_execution_step
                WHERE execution_id=:id ORDER BY seq_no
                """,
                new MapSqlParameterSource("id", executionId),
                rowMapper);
        for (Map<String, Object> stepRow : stepRows) {
            execution.addStep(new AiAgentExecutionStep(
                    ((Number) stepRow.get("seq_no")).intValue(),
                    AiAgentStepType.valueOf(stringValue(stepRow.get("type"))),
                    stringValue(stepRow.get("tool_name")),
                    stringValue(stepRow.get("provider")),
                    AiAgentStepStatus.valueOf(stringValue(stepRow.get("status"))),
                    stepRow.get("duration_ms") == null ? null : ((Number) stepRow.get("duration_ms")).longValue(),
                    stringValue(stepRow.get("error_code")),
                    stringValue(stepRow.get("detail")),
                    null));
        }
        return Optional.of(execution);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static UUID uuidValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Instant instantValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }
}
