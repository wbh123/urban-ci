package org.urbansafe.priority.ai.execution;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

/** 为前端短轮询提供异步人工智能执行任务状态。 */
@Service
public class AiExecutionTaskQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public AiExecutionTaskQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> get(UUID taskId) {
        return jdbc.query(baseSelect() + " WHERE e.id=:id", Map.of("id", taskId),
                (rs, rowNum) -> map(rs)).stream().findFirst().orElseThrow(() ->
                new ResourceNotFoundException(
                        "AI_EXECUTION_TASK_NOT_FOUND", "人工智能异步执行任务不存在"));
    }

    /** 查询指定巡检任务下图片的历史视觉执行，供图库恢复排队状态和路由审计。 */
    public List<Map<String, Object>> listByInspectionTask(UUID inspectionTaskId) {
        return jdbc.query(baseSelect() + """
                JOIN asset.asset_binding b
                  ON b.asset_id=e.asset_id
                 AND b.business_type='INSPECTION_TASK'
                 AND b.business_id=:inspectionTaskId
                 AND b.deleted_at IS NULL
                WHERE e.capability_type='VISION_INFERENCE'
                ORDER BY e.created_at DESC
                """, Map.of("inspectionTaskId", inspectionTaskId),
                (rs, rowNum) -> map(rs));
    }

    private static String baseSelect() {
        return """
                SELECT e.id, e.asset_id, e.status, e.attempt_count, e.max_attempts, e.inference_id,
                       e.error_code, e.error_message, e.created_at, e.started_at, e.finished_at, e.updated_at,
                       e.inputs->>'triggerType' AS trigger_type,
                       e.inputs->>'preferredProvider' AS preferred_provider,
                       e.inputs->>'actualProvider' AS actual_provider,
                       e.inputs->>'orchestrationMode' AS orchestration_mode,
                       COALESCE((e.inputs->>'fallback')::boolean, FALSE) AS fallback,
                       e.inputs->>'fallbackReason' AS fallback_reason
                FROM ai.execution_task e
                """;
    }

    private static Map<String, Object> map(ResultSet rs) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", rs.getObject("id", UUID.class));
        item.put("assetId", rs.getObject("asset_id", UUID.class));
        item.put("status", rs.getString("status"));
        item.put("attemptCount", rs.getInt("attempt_count"));
        item.put("maxAttempts", rs.getInt("max_attempts"));
        item.put("inferenceId", rs.getObject("inference_id", UUID.class));
        item.put("errorCode", rs.getString("error_code"));
        item.put("errorMessage", rs.getString("error_message"));
        item.put("triggerType", rs.getString("trigger_type"));
        item.put("preferredProvider", rs.getString("preferred_provider"));
        item.put("actualProvider", rs.getString("actual_provider"));
        item.put("orchestrationMode", rs.getString("orchestration_mode"));
        item.put("fallback", rs.getBoolean("fallback"));
        item.put("fallbackReason", rs.getString("fallback_reason"));
        item.put("createdAt", rs.getObject("created_at"));
        item.put("startedAt", rs.getObject("started_at"));
        item.put("finishedAt", rs.getObject("finished_at"));
        item.put("updatedAt", rs.getObject("updated_at"));
        return item;
    }
}
