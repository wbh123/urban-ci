package org.urbansafe.priority.ai.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL 异步执行任务仓储，使用租约和 SKIP LOCKED 支持安全领取。 */
@Repository
public class AiExecutionTaskRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiExecutionTaskRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UUID enqueue(AiExecutionCommand command) {
        UUID id = UUID.randomUUID();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("workflowCode", command.workflowCode())
                .addValue("assetId", command.assetId())
                .addValue("mode", command.mode())
                .addValue("modelId", command.modelId())
                .addValue("providerCode", command.providerCode())
                .addValue("capabilityType", command.capabilityType())
                .addValue("prompt", command.prompt())
                .addValue("idempotencyKey", command.idempotencyKey())
                .addValue("requestedBy", command.requestedBy())
                .addValue("inputs", json(command.inputs()));
        int inserted = jdbc.update("""
                INSERT INTO ai.execution_task
                    (id, workflow_code, asset_id, mode, model_id, provider_code, capability_type,
                     prompt, idempotency_key, requested_by, inputs, status, max_attempts)
                SELECT :id, d.workflow_code, :assetId, :mode, :modelId, :providerCode,
                       :capabilityType, :prompt, :idempotencyKey, :requestedBy, CAST(:inputs AS jsonb),
                       'PENDING', d.max_attempts
                FROM ai.workflow_definition d
                WHERE (d.workflow_code=:workflowCode OR d.model_code=:workflowCode)
                  AND d.enabled
                ON CONFLICT (idempotency_key) DO NOTHING
                """, params);
        if (inserted == 1) {
            return id;
        }
        return jdbc.query("""
                SELECT id FROM ai.execution_task WHERE idempotency_key=:idempotencyKey
                """, Map.of("idempotencyKey", command.idempotencyKey()),
                (rs, rowNum) -> rs.getObject("id", UUID.class)).stream().findFirst()
                .orElseThrow(() -> new ResourceConflictException(
                        "AI_EXECUTION_WORKFLOW_NOT_READY",
                        "人工智能工作流未启用或执行任务无法入队"));
    }

    @Transactional
    public Optional<AiExecutionTask> claimNext(String workerId, Duration lease) {
        OffsetDateTime leaseUntil = OffsetDateTime.now().plus(lease);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workerId", workerId)
                .addValue("leaseUntil", leaseUntil);
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id
                    FROM ai.execution_task
                    WHERE status IN ('PENDING','READY','RETRY_WAIT')
                      AND available_at <= CURRENT_TIMESTAMP
                      AND attempt_count < max_attempts
                    ORDER BY available_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE ai.execution_task t
                SET status='RUNNING',
                    attempt_count=t.attempt_count + 1,
                    lease_owner=:workerId,
                    lease_until=:leaseUntil,
                    started_at=COALESCE(t.started_at, CURRENT_TIMESTAMP),
                    updated_at=CURRENT_TIMESTAMP
                FROM candidate c
                WHERE t.id=c.id
                RETURNING t.*
                """, params, (rs, rowNum) -> map(rs)).stream().findFirst();
    }

    public void markSucceeded(UUID id, UUID inferenceId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id).addValue("inferenceId", inferenceId);
        jdbc.update("""
                UPDATE ai.execution_task
                SET status='SUCCEEDED', inference_id=:inferenceId, error_code=NULL, error_message=NULL,
                    lease_owner=NULL, lease_until=NULL, finished_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, params);
    }

    public void markRejected(UUID id, UUID inferenceId, String message) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id).addValue("inferenceId", inferenceId).addValue("message", message);
        jdbc.update("""
                UPDATE ai.execution_task
                SET status='REJECTED', inference_id=:inferenceId, error_code='AI_IMAGE_NOT_APPLICABLE',
                    error_message=:message, lease_owner=NULL, lease_until=NULL,
                    finished_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, params);
    }

    public void markRetry(UUID id, OffsetDateTime availableAt, String errorCode, String errorMessage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id).addValue("availableAt", availableAt)
                .addValue("errorCode", errorCode).addValue("errorMessage", truncate(errorMessage));
        jdbc.update("""
                UPDATE ai.execution_task
                SET status='RETRY_WAIT', available_at=:availableAt, error_code=:errorCode,
                    error_message=:errorMessage, lease_owner=NULL, lease_until=NULL,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, params);
    }

    public void markFailed(UUID id, String errorCode, String errorMessage) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id).addValue("errorCode", errorCode)
                .addValue("errorMessage", truncate(errorMessage));
        jdbc.update("""
                UPDATE ai.execution_task
                SET status='FAILED', error_code=:errorCode, error_message=:errorMessage,
                    lease_owner=NULL, lease_until=NULL, finished_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, params);
    }

    public int recoverExpiredLeases() {
        return jdbc.update("""
                UPDATE ai.execution_task
                SET status=CASE WHEN attempt_count < max_attempts THEN 'RETRY_WAIT' ELSE 'FAILED' END,
                    available_at=CURRENT_TIMESTAMP,
                    error_code='AI_EXECUTION_LEASE_EXPIRED',
                    error_message='执行工作器租约过期，任务已恢复',
                    lease_owner=NULL,
                    lease_until=NULL,
                    finished_at=CASE WHEN attempt_count >= max_attempts THEN CURRENT_TIMESTAMP ELSE finished_at END,
                    updated_at=CURRENT_TIMESTAMP
                WHERE status='RUNNING' AND lease_until < CURRENT_TIMESTAMP
                """, Map.of());
    }

    private AiExecutionTask map(ResultSet rs) throws SQLException {
        return new AiExecutionTask(
                rs.getObject("id", UUID.class),
                rs.getObject("asset_id", UUID.class),
                rs.getString("workflow_code"),
                rs.getString("mode"),
                rs.getString("model_id"),
                rs.getString("provider_code"),
                rs.getString("capability_type"),
                rs.getString("prompt"),
                rs.getString("idempotency_key"),
                rs.getObject("requested_by", UUID.class),
                parseMap(rs.getString("inputs")),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                rs.getInt("max_attempts"),
                rs.getObject("available_at", OffsetDateTime.class),
                rs.getString("lease_owner"),
                rs.getObject("lease_until", OffsetDateTime.class),
                rs.getObject("inference_id", UUID.class),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("人工智能执行输入无法序列化", ex);
        }
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("人工智能执行输入无法反序列化", ex);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
