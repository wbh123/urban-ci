package org.urbansafe.priority.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.ai.orchestration.AiBoundingBoxNormalizer;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.provider.AiProviderException;

/** 第七阶段编排审计与统一结果持久层，不修改第三阶段冻结仓储语义。 */
@Repository
public class AiOrchestrationRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AiOrchestrationRepository(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordRouting(
            UUID taskId,
            String providerCode,
            String capabilityType,
            String workflowCode,
            String workflowVersion) {
        jdbc.update("""
                UPDATE ai.inference_task
                SET provider_code=:providerCode,
                    capability_type=:capabilityType,
                    workflow_code=:workflowCode,
                    workflow_version=:workflowVersion,
                    fallback_used=FALSE,
                    fallback_provider_code=NULL,
                    fallback_reason=NULL,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=:taskId
                """, new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("providerCode", providerCode)
                .addValue("capabilityType", capabilityType)
                .addValue("workflowCode", workflowCode)
                .addValue("workflowVersion", workflowVersion));
    }

    @Transactional
    public void saveSuccess(UUID taskId, AiStructuredResult result) {
        UUID resultId = UUID.randomUUID();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("summary", result.summary());
        summary.put("detectionCount", result.detections().size());
        summary.put("riskSignals", result.riskSignals());
        summary.put("recommendations", result.recommendations());
        summary.put("confidence", result.confidence());

        Map<String, Object> auditSnapshot = new LinkedHashMap<>();
        auditSnapshot.put("requestId", result.requestId());
        auditSnapshot.put("providerCode", result.providerCode());
        auditSnapshot.put("modelCode", result.modelCode());
        auditSnapshot.put("modelVersion", result.modelVersion());
        auditSnapshot.put("capabilityType", result.capabilityType());
        auditSnapshot.put("status", result.status());
        auditSnapshot.put("rawResponseReference", result.rawResponseReference());

        jdbc.update("""
                INSERT INTO ai.inference_result
                  (id, inference_task_id, quality_status, applicability,
                   summary, raw_output_snapshot, warning_messages,
                   structured_result, raw_response_reference)
                VALUES (:id,:taskId,'ACCEPTABLE',:applicability,
                   CAST(:summary AS jsonb),CAST(:snapshot AS jsonb),CAST(:warnings AS jsonb),
                   CAST(:structuredResult AS jsonb),:rawResponseReference)
                """, new MapSqlParameterSource()
                .addValue("id", resultId)
                .addValue("taskId", taskId)
                .addValue("applicability", "REJECTED".equals(result.status())
                        ? "NOT_APPLICABLE" : "APPLICABLE")
                .addValue("summary", json(summary))
                .addValue("snapshot", json(auditSnapshot))
                .addValue("warnings", json(result.warnings()))
                .addValue("structuredResult", json(result))
                .addValue("rawResponseReference", result.rawResponseReference()));

        int sequence = 1;
        for (AiStructuredResult.Detection detection : result.detections()) {
            AiStructuredResult.BoundingBox box = detection.boundingBox();
            AiBoundingBoxNormalizer.Box normalized = normalizeBox(box);
            jdbc.update("""
                    INSERT INTO ai.detection
                      (id,inference_result_id,sequence_no,class_code,class_name,confidence,
                       bbox_x,bbox_y,bbox_width,bbox_height,coordinate_type,extra_data)
                    VALUES (:id,:resultId,:sequence,:classCode,:className,:confidence,
                       :x,:y,:width,:height,:coordinateType,CAST(:extraData AS jsonb))
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("resultId", resultId)
                    .addValue("sequence", sequence++)
                    .addValue("classCode", defaultText(detection.classCode(), "UNKNOWN"))
                    .addValue("className", defaultText(detection.className(), "未分类候选"))
                    .addValue("confidence", defaultConfidence(detection.confidence()))
                    .addValue("x", normalized.x())
                    .addValue("y", normalized.y())
                    .addValue("width", normalized.width())
                    .addValue("height", normalized.height())
                    .addValue("coordinateType", defaultText(
                            box.coordinateType(), "NORMALIZED_XYWH"))
                    .addValue("extraData", "{}"));
        }

        jdbc.update("""
                UPDATE ai.inference_task
                SET status='SUCCEEDED', completed_at=CURRENT_TIMESTAMP,
                    duration_ms=:durationMs, error_code=NULL, error_message=NULL,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                WHERE id=:taskId
                """, Map.of("taskId", taskId, "durationMs", result.durationMs()));
    }

    public Optional<Map<String, Object>> findAudit(UUID taskId) {
        var rows = jdbc.query("""
                SELECT t.provider_code AS "providerCode",
                       t.capability_type AS "capabilityType",
                       t.workflow_code AS "workflowCode",
                       t.workflow_version AS "workflowVersion",
                       t.fallback_used AS "fallbackUsed",
                       t.fallback_provider_code AS "fallbackProviderCode",
                       t.fallback_reason AS "fallbackReason",
                       r.structured_result AS "structuredResult",
                       r.raw_response_reference AS "rawResponseReference"
                FROM ai.inference_task t
                LEFT JOIN ai.inference_result r ON r.inference_task_id=t.id
                WHERE t.id=:taskId
                """, Map.of("taskId", taskId), rowMapper);
        return rows.stream().findFirst().map(this::normalizeJsonColumns);
    }

    private Map<String, Object> normalizeJsonColumns(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        Object structuredResult = normalized.get("structuredResult");
        if (structuredResult == null) {
            return normalized;
        }
        String jsonValue = String.valueOf(structuredResult);
        if (jsonValue.isBlank() || "null".equalsIgnoreCase(jsonValue)) {
            normalized.put("structuredResult", null);
            return normalized;
        }
        try {
            normalized.put("structuredResult", objectMapper.readValue(
                    jsonValue, new TypeReference<Map<String, Object>>() { }));
            return normalized;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("人工智能结构化结果反序列化失败", ex);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("人工智能结构化结果序列化失败", ex);
        }
    }

    /** 统一归一化并校验检测框；真正非法坐标抛出明确异常，绝不静默丢弃。 */
    private static AiBoundingBoxNormalizer.Box normalizeBox(AiStructuredResult.BoundingBox box) {
        if (box == null) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "检测对象缺少检测框");
        }
        return AiBoundingBoxNormalizer.normalize(box.x(), box.y(), box.width(), box.height());
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static double defaultConfidence(Double value) {
        if (value == null) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }
}
