package org.urbansafe.priority.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/**
 * 人工智能推理持久层集成测试，验证推理表结构、约束与真实读写。
 */
class AiInferenceRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private AiInferenceRepository repository;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID communityId;
    private UUID buildingId;
    private UUID assetId;
    private UUID modelRegistryId;
    private UUID inspectionTaskId;

    @BeforeEach
    void setUp() {
        // 每个测试使用全新的 UUID、用户、资产和业务对象，不需要删除共享数据库中的其他测试数据。
        // 避免全库 DELETE/TRUNCATE 破坏楼栋证据、公众反馈或迁移预置的模型登记记录。
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.user_account(id,username,password_hash) VALUES (?,?,?)",
                userId, "ai-test-" + userId.toString().substring(0, 8), "hash");

        communityId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.community(id,community_code,community_name) VALUES (?,?,?)",
                communityId, "C-AI-" + communityId.toString().substring(0, 6), "AI测试小区");

        buildingId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.building(id,community_id,building_code) VALUES (?,?,?)",
                buildingId, communityId, "B-AI");

        assetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO asset.file_asset(id,bucket_name,object_key,original_filename,
                  content_type,file_size,sha256,business_type,business_id,storage_provider)
                VALUES (?,?,?,?,?,?,?,?,?,'MINIO')
                """, assetId, "bucket", "key/" + assetId, "image.jpg", "image/jpeg", 100,
                "0000000000000000000000000000000000000000000000000000000000000000",
                "INSPECTION_TASK", buildingId);

        inspectionTaskId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.inspection_task(id,task_code,building_id,inspection_type,status) VALUES (?,?,?,?,?)",
                inspectionTaskId, "IT-AI-" + inspectionTaskId.toString().substring(0, 6),
                buildingId, "ROUTINE", "PENDING");

        jdbc.update("INSERT INTO asset.asset_binding(asset_id,business_type,business_id,binding_role) VALUES (?,?,?,?)",
                assetId, "INSPECTION_TASK", inspectionTaskId, "INSPECTION_PHOTO");

        Optional<Map<String, Object>> mockModel = repository.findModelByCode("AI-DEFECT-MOCK-001");
        assertThat(mockModel).isPresent();
        modelRegistryId = (UUID) mockModel.get().get("id");
    }

    @Test
    void mockModelRegisteredByMigration() {
        Optional<Map<String, Object>> model = repository.findModelByCode("AI-DEFECT-MOCK-001");
        assertThat(model).isPresent();
        assertThat(model.get().get("mode")).isEqualTo("MOCK");
        assertThat(model.get().get("status")).isEqualTo("MOCK");
        assertThat(model.get().get("licenseName")).isEqualTo("PROJECT-INTERNAL-MOCK");
    }

    @Test
    void approvedCudaCrackModelRegisteredByMigration() {
        Optional<Map<String, Object>> model = repository.findModelByCode("AI-CRACK-HF-UNET-001");
        assertThat(model).isPresent();
        assertThat(model.get().get("mode")).isEqualTo("REAL");
        assertThat(model.get().get("status")).isEqualTo("APPROVED");
        assertThat(model.get().get("licenseName")).isEqualTo("MIT");
        assertThat(model.get().get("weightSha256"))
                .isEqualTo("4deff4d3a21e8b01e547c57b07398a0d9f9794534a61c978722390ef1f49a4a2");
    }

    @Test
    void resolveAssetTraceabilityShouldDeriveBuildingAndTask() {
        Optional<Map<String, Object>> trace = repository.resolveAssetTraceability(assetId);
        assertThat(trace).isPresent();
        assertThat(trace.get().get("buildingId")).isEqualTo(buildingId);
        assertThat(trace.get().get("communityId")).isEqualTo(communityId);
        assertThat(trace.get().get("inspectionTaskId")).isEqualTo(inspectionTaskId);
    }

    @Test
    void saveSuccessShouldPersistResultAndDetections() {
        UUID taskId = insertPendingTask("key-1");
        repository.markRunning(taskId);
        AiInferenceResponse response = buildResponse();
        repository.saveSuccess(taskId, response);

        Optional<Map<String, Object>> detail = repository.findTaskDetail(taskId);
        assertThat(detail).isPresent();
        assertThat(detail.get().get("status")).isEqualTo("SUCCEEDED");
        assertThat(detail.get().get("applicability")).isEqualTo("APPLICABLE");
        assertThat(detail.get().get("summary")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) detail.get().get("summary")).get("detectionCount")).isEqualTo(1);
        assertThat(detail.get().get("warnings")).isInstanceOf(List.class);
        assertThat(((List<?>) detail.get().get("warnings")).getFirst()).isEqualTo("模拟结果仅用于业务链路验证");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detections = (List<Map<String, Object>>) detail.get().get("detections");
        assertThat(detections).hasSize(1);
        assertThat(detections.get(0).get("classCode")).isEqualTo("CRACK");

        Object boundingBox = detections.get(0).get("boundingBox");
        assertThat(boundingBox).isInstanceOf(Map.class);
        Map<?, ?> box = (Map<?, ?>) boundingBox;
        assertThat(box.get("x")).isInstanceOf(Number.class);
        assertThat(((Number) box.get("x")).doubleValue()).isEqualTo(0.1d);
        assertThat(((Number) box.get("y")).doubleValue()).isEqualTo(0.1d);
        assertThat(((Number) box.get("width")).doubleValue()).isEqualTo(0.2d);
        assertThat(((Number) box.get("height")).doubleValue()).isEqualTo(0.2d);
        assertThat(box.get("coordinateType")).isEqualTo("NORMALIZED_XYWH");
    }

    @Test
    void idempotencyShouldRejectDuplicateActiveTask() {
        insertPendingTask("dup-key");
        assertThatThrownBy(() -> insertPendingTask("dup-key"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void detectionBoxConstraintShouldRejectInvalidCoordinates() {
        UUID taskId = insertPendingTask("box-key");
        repository.markRunning(taskId);
        UUID resultId = UUID.randomUUID();
        namedJdbc.update("""
                INSERT INTO ai.inference_result(id,inference_task_id,image_width,image_height,
                  quality_status,applicability,summary,raw_output_snapshot,warning_messages)
                VALUES (:id,:taskId,64,64,'ACCEPTABLE','APPLICABLE','{}'::jsonb,'{}'::jsonb,'[]'::jsonb)
                """, new MapSqlParameterSource().addValue("id", resultId).addValue("taskId", taskId));

        assertThatThrownBy(() -> namedJdbc.update("""
                INSERT INTO ai.detection(id,inference_result_id,sequence_no,class_code,class_name,
                  confidence,bbox_x,bbox_y,bbox_width,bbox_height,coordinate_type,extra_data)
                VALUES (:id,:resultId,1,'CRACK','裂缝',0.5,1.5,0.1,0.2,0.2,'NORMALIZED_XYWH','{}'::jsonb)
                """, new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("resultId", resultId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveFailureShouldPersistFailedStatusAndErrorCode() {
        UUID taskId = insertPendingTask("fail-key");
        repository.markRunning(taskId);
        repository.saveFailure(taskId, "AI_SERVICE_TIMEOUT", "超时", false);

        Optional<Map<String, Object>> detail = repository.findTaskDetail(taskId);
        assertThat(detail).isPresent();
        assertThat(detail.get().get("status")).isEqualTo("FAILED");
        assertThat(detail.get().get("errorCode")).isEqualTo("AI_SERVICE_TIMEOUT");
    }

    @Test
    void saveReviewShouldUpdateReviewStatus() {
        UUID taskId = insertPendingTask("review-key");
        repository.markRunning(taskId);
        repository.saveSuccess(taskId, buildResponse());
        repository.saveReview(taskId, "CONFIRMED", "确认", userId);

        Optional<Map<String, Object>> detail = repository.findTaskDetail(taskId);
        assertThat(detail).isPresent();
        assertThat(detail.get().get("reviewStatus")).isEqualTo("CONFIRMED");
    }

    private UUID insertPendingTask(String idempotencyKey) {
        UUID taskId = UUID.randomUUID();
        repository.insertTask(taskId, "AI-TEST-" + taskId.toString().substring(0, 8),
                idempotencyKey, assetId, inspectionTaskId, null, buildingId, communityId,
                modelRegistryId, "MOCK", 1, userId);
        return taskId;
    }

    private AiInferenceResponse buildResponse() {
        return new AiInferenceResponse(
                "UNKNOWN", "SUCCEEDED", "MOCK",
                new AiInferenceResponse.ModelBrief("AI-DEFECT-MOCK-001", "Mock", "0.1.0"),
                new AiInferenceResponse.ImageInfo(64, 64, "ACCEPTABLE", "APPLICABLE"),
                List.of(new AiInferenceResponse.Detection(
                        1, "CRACK", "裂缝", 0.82,
                        new AiInferenceResponse.BoundingBox(0.1, 0.1, 0.2, 0.2, "NORMALIZED_XYWH"),
                        null)),
                new AiInferenceResponse.Summary(1, Map.of("CRACK", 1)),
                12L,
                List.of("模拟结果仅用于业务链路验证"));
    }
}
