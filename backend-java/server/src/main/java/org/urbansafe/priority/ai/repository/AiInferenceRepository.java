package org.urbansafe.priority.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.ai.client.AiInferenceResponse;

/**
 * 人工智能推理持久层，使用 NamedParameterJdbcTemplate 与受控 SQL，
 * 与第二阶段持久层风格保持一致，不引入 MyBatis-Plus 实体。
 */
@Repository
public class AiInferenceRepository {

    private static final List<String> IMAGE_REJECTION_CODES = List.of(
            "AI_IMAGE_EMPTY", "AI_IMAGE_UNSUPPORTED_FORMAT", "AI_IMAGE_DECODE_FAILED",
            "AI_IMAGE_TOO_LARGE", "AI_IMAGE_NOT_APPLICABLE", "AI_IMAGE_LOW_QUALITY");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AiInferenceRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** 查询模型登记。 */
    public Optional<Map<String, Object>> findModelByCode(String modelCode) {
        return one("""
                SELECT id AS "id",
                       model_code AS "modelCode",
                       model_name AS "modelName",
                       model_version AS "modelVersion",
                       framework AS "framework",
                       mode AS "mode",
                       status AS "status",
                       license_name AS "licenseName",
                       weight_filename AS "weightFilename",
                       weight_sha256 AS "weightSha256",
                       supported_classes AS "supportedClasses",
                       limitations AS "limitations",
                       deployment_stage AS "deploymentStage",
                       formal_evidence_enabled AS "formalEvidenceEnabled",
                       approved_at AS "approvedAt"
                FROM ai.model_registry
                WHERE model_code=:code AND deleted_at IS NULL
                """, Map.of("code", modelCode));
    }

    /** 按主键查询模型登记。 */
    public Optional<Map<String, Object>> findModelById(UUID modelRegistryId) {
        return one("""
                SELECT id AS "id",
                       model_code AS "modelCode",
                       model_name AS "modelName",
                       model_version AS "modelVersion",
                       framework AS "framework",
                       mode AS "mode",
                       status AS "status",
                       license_name AS "licenseName",
                       weight_filename AS "weightFilename",
                       weight_sha256 AS "weightSha256",
                       supported_classes AS "supportedClasses",
                       limitations AS "limitations",
                       deployment_stage AS "deploymentStage",
                       formal_evidence_enabled AS "formalEvidenceEnabled",
                       approved_at AS "approvedAt"
                FROM ai.model_registry
                WHERE id=:id AND deleted_at IS NULL
                """, Map.of("id", modelRegistryId));
    }

    /** 解析图片资产追溯到楼栋、小区、巡检任务与记录。 */
    public Optional<Map<String, Object>> resolveAssetTraceability(UUID assetId) {
        List<Map<String, Object>> bindings = jdbc.query("""
                SELECT business_type AS "businessType", business_id AS "businessId"
                FROM asset.asset_binding
                WHERE asset_id=:assetId AND deleted_at IS NULL
                ORDER BY binding_role
                """, Map.of("assetId", assetId), rowMapper);

        UUID buildingId = null;
        UUID inspectionTaskId = null;
        UUID inspectionRecordId = null;

        for (String businessType : List.of("INSPECTION_RECORD", "INSPECTION_TASK", "BUILDING")) {
            for (Map<String, Object> binding : bindings) {
                if (!businessType.equals(String.valueOf(binding.get("businessType")))) {
                    continue;
                }
                UUID businessId = (UUID) binding.get("businessId");
                if (businessId == null) {
                    continue;
                }
                if (buildingId == null && "BUILDING".equals(businessType)) {
                    buildingId = businessId;
                } else if (buildingId == null && "INSPECTION_TASK".equals(businessType)) {
                    buildingId = queryBuildingId(
                            "SELECT building_id FROM core.inspection_task WHERE id=:id AND deleted_at IS NULL",
                            businessId);
                    inspectionTaskId = businessId;
                } else if (buildingId == null && "INSPECTION_RECORD".equals(businessType)) {
                    buildingId = queryBuildingId(
                            "SELECT building_id FROM core.inspection_record WHERE id=:id AND deleted_at IS NULL",
                            businessId);
                    inspectionRecordId = businessId;
                }
            }
            if (buildingId != null) {
                break;
            }
        }
        if (buildingId == null) {
            return Optional.empty();
        }

        UUID communityId = queryCommunityId(buildingId);
        if (communityId == null) {
            return Optional.empty();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId);
        result.put("communityId", communityId);
        result.put("inspectionTaskId", inspectionTaskId);
        result.put("inspectionRecordId", inspectionRecordId);
        return Optional.of(result);
    }

    private UUID queryBuildingId(String sql, UUID businessId) {
        try {
            return jdbc.queryForObject(sql, Map.of("id", businessId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private UUID queryCommunityId(UUID buildingId) {
        try {
            return jdbc.queryForObject(
                    "SELECT community_id FROM core.building WHERE id=:id AND deleted_at IS NULL",
                    Map.of("id", buildingId), UUID.class);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /** 查询活跃的幂等任务。 */
    public Optional<Map<String, Object>> findActiveTask(
            UUID requestedBy, UUID assetId, String mode, UUID modelRegistryId, String idempotencyKey) {
        StringBuilder sql = new StringBuilder("""
                SELECT id AS "inferenceId", status AS "status"
                FROM ai.inference_task
                WHERE requested_by=:requestedBy AND asset_id=:assetId AND mode=:mode
                  AND model_registry_id=:modelRegistryId AND status IN ('PENDING','RUNNING')
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("requestedBy", requestedBy)
                .addValue("assetId", assetId)
                .addValue("mode", mode)
                .addValue("modelRegistryId", modelRegistryId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            sql.append(" AND idempotency_key=:idempotencyKey");
            params.addValue("idempotencyKey", idempotencyKey);
        } else {
            sql.append(" AND idempotency_key IS NULL");
        }
        sql.append(" ORDER BY created_at DESC LIMIT 1");
        return one(sql.toString(), params.getValues());
    }

    /** 查询同一图片、模式、模型的最大尝试序号。 */
    public int findMaxAttemptNo(UUID assetId, String mode, UUID modelRegistryId) {
        Integer count = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_no), 0) FROM ai.inference_task
                WHERE asset_id=:assetId AND mode=:mode AND model_registry_id=:modelRegistryId
                """, Map.of("assetId", assetId, "mode", mode, "modelRegistryId", modelRegistryId),
                Integer.class);
        return count == null ? 0 : count;
    }

    /** 创建 PENDING 推理任务。 */
    @Transactional
    public void insertTask(UUID id, String requestCode, String idempotencyKey, UUID assetId,
            UUID inspectionTaskId, UUID inspectionRecordId, UUID buildingId, UUID communityId,
            UUID modelRegistryId, String mode, int attemptNo, UUID requestedBy) {
        jdbc.update("""
                INSERT INTO ai.inference_task
                  (id, request_code, idempotency_key, asset_id, inspection_task_id,
                   inspection_record_id, building_id, community_id, model_registry_id,
                   mode, status, attempt_no, review_status, requested_by, requested_at, version)
                VALUES (:id,:requestCode,:idempotencyKey,:assetId,:inspectionTaskId,
                   :inspectionRecordId,:buildingId,:communityId,:modelRegistryId,
                   :mode,'PENDING',:attemptNo,'UNREVIEWED',:requestedBy,CURRENT_TIMESTAMP,0)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("requestCode", requestCode)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("assetId", assetId)
                .addValue("inspectionTaskId", inspectionTaskId)
                .addValue("inspectionRecordId", inspectionRecordId)
                .addValue("buildingId", buildingId)
                .addValue("communityId", communityId)
                .addValue("modelRegistryId", modelRegistryId)
                .addValue("mode", mode)
                .addValue("attemptNo", attemptNo)
                .addValue("requestedBy", requestedBy));
    }

    /** 标记任务为 RUNNING。 */
    @Transactional
    public int markRunning(UUID taskId) {
        return jdbc.update("""
                UPDATE ai.inference_task
                SET status='RUNNING', started_at=CURRENT_TIMESTAMP, version=version+1,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status='PENDING'
                """, Map.of("id", taskId));
    }

    /** 保存成功结果、检测对象并更新 SUCCEEDED。 */
    @Transactional
    public void saveSuccess(UUID taskId, AiInferenceResponse response) {
        UUID resultId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai.inference_result
                  (id, inference_task_id, image_width, image_height, quality_status,
                   applicability, summary, raw_output_snapshot, warning_messages)
                VALUES (:id,:taskId,:width,:height,:qualityStatus,:applicability,
                   CAST(:summary AS jsonb), CAST(:snapshot AS jsonb), CAST(:warnings AS jsonb))
                """, new MapSqlParameterSource()
                .addValue("id", resultId)
                .addValue("taskId", taskId)
                .addValue("width", response.image().width())
                .addValue("height", response.image().height())
                .addValue("qualityStatus", response.image().qualityStatus())
                .addValue("applicability", response.image().applicability())
                .addValue("summary", json(response.summary()))
                .addValue("snapshot", json(response))
                .addValue("warnings", json(response.warnings())));
        int sequence = 1;
        for (AiInferenceResponse.Detection detection : response.detections()) {
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
                    .addValue("classCode", detection.classCode())
                    .addValue("className", detection.className())
                    .addValue("confidence", detection.confidence())
                    .addValue("x", detection.boundingBox().x())
                    .addValue("y", detection.boundingBox().y())
                    .addValue("width", detection.boundingBox().width())
                    .addValue("height", detection.boundingBox().height())
                    .addValue("coordinateType", detection.boundingBox().coordinateType())
                    .addValue("extraData", json(Map.of())));
        }
        jdbc.update("""
                UPDATE ai.inference_task
                SET status='SUCCEEDED', completed_at=CURRENT_TIMESTAMP,
                    duration_ms=:durationMs, error_code=NULL, error_message=NULL,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, Map.of("id", taskId, "durationMs", response.durationMs()));
    }

    /** 保存失败或拒绝状态。 */
    @Transactional
    public void saveFailure(UUID taskId, String errorCode, String errorMessage, boolean rejected) {
        jdbc.update("""
                UPDATE ai.inference_task
                SET status=:status, completed_at=CURRENT_TIMESTAMP,
                    error_code=:errorCode, error_message=:errorMessage,
                    version=version+1, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id
                """, Map.of(
                        "id", taskId,
                        "status", rejected ? "REJECTED" : "FAILED",
                        "errorCode", errorCode,
                        "errorMessage", errorMessage));
    }

    public boolean isImageRejection(String errorCode) {
        return IMAGE_REJECTION_CODES.contains(errorCode);
    }

    public Optional<Map<String, Object>> findTaskStatus(UUID inferenceId) {
        return one("""
                SELECT id AS "inferenceId", status AS "status", mode AS "mode",
                       asset_id AS "assetId", model_registry_id AS "modelRegistryId",
                       review_status AS "reviewStatus"
                FROM ai.inference_task WHERE id=:id
                """, Map.of("id", inferenceId));
    }

    public Optional<Map<String, Object>> findTaskDetail(UUID inferenceId) {
        Optional<Map<String, Object>> task = one("""
                SELECT t.id AS "inferenceId", t.request_code AS "requestCode",
                       t.status AS "status", t.mode AS "mode", t.asset_id AS "assetId",
                       t.inspection_task_id AS "inspectionTaskId",
                       t.inspection_record_id AS "inspectionRecordId",
                       t.building_id AS "buildingId", t.community_id AS "communityId",
                       t.requested_by AS "requestedBy", t.attempt_no AS "attemptNo",
                       t.review_status AS "reviewStatus", t.duration_ms AS "durationMs",
                       t.error_code AS "errorCode", t.error_message AS "errorMessage",
                       t.requested_at AS "requestedAt", t.started_at AS "startedAt",
                       t.completed_at AS "completedAt", t.created_at AS "createdAt",
                       t.version AS "version", m.model_code AS "modelId",
                       m.model_name AS "modelName", m.model_version AS "modelVersion",
                       m.license_name AS "license",
                       m.deployment_stage AS "deploymentStage",
                       m.formal_evidence_enabled AS "formalEvidenceEnabled",
                       r.image_width AS "imageWidth", r.image_height AS "imageHeight",
                       r.quality_status AS "qualityStatus", r.applicability AS "applicability",
                       r.summary AS "summary", r.warning_messages AS "warnings"
                FROM ai.inference_task t
                JOIN ai.model_registry m ON m.id=t.model_registry_id
                LEFT JOIN ai.inference_result r ON r.inference_task_id=t.id
                WHERE t.id=:id
                """, Map.of("id", inferenceId));
        if (task.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> detail = normalizeDetailJsonColumns(task.get());
        detail.put("detections", listDetections(inferenceId));
        detail.put("latestReview", latestReview(inferenceId).orElse(null));
        return Optional.of(detail);
    }

    private Map<String, Object> normalizeDetailJsonColumns(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>(source);
        normalized.put("summary", readJsonColumn(normalized.get("summary"), Map.of()));
        normalized.put("warnings", readJsonColumn(normalized.get("warnings"), List.of()));
        return normalized;
    }

    private Object readJsonColumn(Object value, Object emptyValue) {
        if (value == null) {
            return emptyValue;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return value;
        }
        String jsonValue = String.valueOf(value);
        if (jsonValue.isBlank() || "null".equalsIgnoreCase(jsonValue)) {
            return emptyValue;
        }
        try {
            return objectMapper.readValue(jsonValue, new TypeReference<Object>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("人工智能推理 JSON 字段反序列化失败", ex);
        }
    }

    public List<Map<String, Object>> listTasks(Map<String, Object> filters, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.id AS "inferenceId", t.request_code AS "requestCode",
                       t.status AS "status", t.mode AS "mode", t.asset_id AS "assetId",
                       t.inspection_task_id AS "inspectionTaskId",
                       t.inspection_record_id AS "inspectionRecordId",
                       t.building_id AS "buildingId", t.community_id AS "communityId",
                       t.review_status AS "reviewStatus", t.attempt_no AS "attemptNo",
                       t.duration_ms AS "durationMs", t.error_code AS "errorCode",
                       t.error_message AS "errorMessage", t.created_at AS "createdAt",
                       t.completed_at AS "completedAt", m.model_code AS "modelId",
                       m.model_name AS "modelName", m.model_version AS "modelVersion",
                       m.license_name AS "license",
                       m.deployment_stage AS "deploymentStage",
                       m.formal_evidence_enabled AS "formalEvidenceEnabled",
                       COALESCE((SELECT COUNT(*) FROM ai.detection d
                                JOIN ai.inference_result ir ON ir.id=d.inference_result_id
                                WHERE ir.inference_task_id=t.id),0) AS "detectionCount"
                FROM ai.inference_task t
                JOIN ai.model_registry m ON m.id=t.model_registry_id
                WHERE 1=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendFilters(sql, params, filters);
        sql.append(" ORDER BY t.created_at DESC LIMIT :size OFFSET :offset");
        params.addValue("size", size).addValue("offset", page * size);
        return jdbc.query(sql.toString(), params, rowMapper);
    }

    public long countTasks(Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM ai.inference_task t
                JOIN ai.model_registry m ON m.id=t.model_registry_id
                WHERE 1=1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        appendFilters(sql, params, filters);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0 : count;
    }

    @Transactional
    public void saveReview(UUID inferenceId, String reviewStatus, String comment, UUID reviewedBy) {
        jdbc.update("""
                INSERT INTO ai.inference_review
                  (id,inference_task_id,review_status,review_comment,reviewed_by,reviewed_at,corrected_data)
                VALUES (:id,:inferenceId,:reviewStatus,:comment,:reviewedBy,CURRENT_TIMESTAMP,'{}'::jsonb)
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("inferenceId", inferenceId)
                .addValue("reviewStatus", reviewStatus)
                .addValue("comment", comment)
                .addValue("reviewedBy", reviewedBy));
        jdbc.update("""
                UPDATE ai.inference_task
                SET review_status=:reviewStatus, updated_at=CURRENT_TIMESTAMP, version=version+1
                WHERE id=:id
                """, Map.of("id", inferenceId, "reviewStatus", reviewStatus));
    }

    private List<Map<String, Object>> listDetections(UUID inferenceId) {
        return jdbc.query("""
                SELECT d.sequence_no AS "sequence", d.class_code AS "classCode",
                       d.class_name AS "className", d.confidence AS "confidence",
                       jsonb_build_object(
                         'x',d.bbox_x,'y',d.bbox_y,'width',d.bbox_width,'height',d.bbox_height,
                         'coordinateType',d.coordinate_type) AS "boundingBox"
                FROM ai.detection d
                JOIN ai.inference_result r ON r.id=d.inference_result_id
                WHERE r.inference_task_id=:id
                ORDER BY d.sequence_no
                """, Map.of("id", inferenceId), rowMapper);
    }

    private Optional<Map<String, Object>> latestReview(UUID inferenceId) {
        return one("""
                SELECT review_status AS "reviewStatus", review_comment AS "comment",
                       reviewed_by AS "reviewedBy", reviewed_at AS "reviewedAt"
                FROM ai.inference_review
                WHERE inference_task_id=:id
                ORDER BY reviewed_at DESC, id DESC LIMIT 1
                """, Map.of("id", inferenceId));
    }

    private void appendFilters(StringBuilder sql, MapSqlParameterSource params, Map<String, Object> filters) {
        addFilter(sql, params, filters, "status", "t.status");
        addFilter(sql, params, filters, "mode", "t.mode");
        addFilter(sql, params, filters, "modelId", "m.model_code");
        addFilter(sql, params, filters, "providerCode", "t.provider_code");
        addFilter(sql, params, filters, "capabilityType", "t.capability_type");
        addFilter(sql, params, filters, "assetId", "t.asset_id");
        addFilter(sql, params, filters, "inspectionTaskId", "t.inspection_task_id");
        addFilter(sql, params, filters, "inspectionRecordId", "t.inspection_record_id");
        addFilter(sql, params, filters, "buildingId", "t.building_id");
        addFilter(sql, params, filters, "communityId", "t.community_id");
    }

    private void addFilter(StringBuilder sql, MapSqlParameterSource params,
            Map<String, Object> filters, String key, String column) {
        Object value = filters.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            sql.append(" AND ").append(column).append("=:").append(key);
            params.addValue(key, value);
        }
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("人工智能推理数据序列化失败", ex);
        }
    }
}
