package org.urbansafe.priority.assessment.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.ai.repository.AiInferenceRepository;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class AssessmentInputRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private AssessmentInputRepository repository;

    @Autowired
    private AiInferenceRepository aiRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;
    private UUID communityId;
    private UUID buildingId;
    private UUID assetId;
    private UUID modelRegistryId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.user_account(id,username,password_hash) VALUES (?,?,?)",
                userId, "assessment-input-" + userId.toString().substring(0, 8), "hash");

        communityId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.community(id,community_code,community_name) VALUES (?,?,?)",
                communityId, "C-ASSESS-" + communityId.toString().substring(0, 6), "评分输入小区");

        buildingId = UUID.randomUUID();
        jdbc.update("INSERT INTO core.building(id,community_id,building_code,building_name) VALUES (?,?,?,?)",
                buildingId, communityId, "B-ASSESS", "评分输入楼栋");

        assetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO asset.file_asset(id,bucket_name,object_key,original_filename,
                  content_type,file_size,sha256,business_type,business_id,storage_provider)
                VALUES (?,?,?,?,?,?,?,?,?,'MINIO')
                """, assetId, "bucket", "key/" + assetId, "image.jpg", "image/jpeg", 100,
                "1111111111111111111111111111111111111111111111111111111111111111",
                "BUILDING", buildingId);

        modelRegistryId = (UUID) aiRepository.findModelByCode("AI-DEFECT-MOCK-001")
                .orElseThrow().get("id");
    }

    @Test
    void confirmedRealInferenceAggregatesAllDetectionsByStableDefectKey() {
        UUID taskId = insertSucceededTask("CONFIRMED", "confirmed");
        UUID resultId = insertResult(taskId);
        insertDetection(resultId, 1, "CRACK", "SEVERE", "外墙");
        insertDetection(resultId, 2, "CRACK", "SEVERE", "外墙");
        insertDetection(resultId, 3, "SPALLING", "MODERATE", "楼道");

        List<Map<String, Object>> evidence = repository.findEligibleAiEvidence(buildingId);

        assertThat(evidence).hasSize(2);
        assertThat(evidence).anySatisfy(row -> {
            assertThat(row.get("defectType")).isEqualTo("CRACK");
            assertThat(row.get("severity")).isEqualTo("SEVERE");
            assertThat(row.get("part")).isEqualTo("外墙");
            assertThat(row.get("quantity")).isEqualTo(2);
        });
        assertThat(evidence).anySatisfy(row -> {
            assertThat(row.get("defectType")).isEqualTo("SPALLING");
            assertThat(row.get("quantity")).isEqualTo(1);
        });
    }

    @Test
    void correctedRealInferenceUsesLatestCorrectedDefectsArray() {
        UUID taskId = insertSucceededTask("CORRECTED", "corrected");
        insertResult(taskId);
        jdbc.update("""
                INSERT INTO ai.inference_review(id,inference_task_id,review_status,review_comment,
                  reviewed_by,reviewed_at,corrected_data)
                VALUES (gen_random_uuid(), ?, 'CORRECTED', 'old', ?, CURRENT_TIMESTAMP - INTERVAL '1 hour',
                  '{"defectType":"CRACK","severity":"MINOR","quantity":1,"part":"外墙"}'::jsonb)
                """, taskId, userId);
        jdbc.update("""
                INSERT INTO ai.inference_review(id,inference_task_id,review_status,review_comment,
                  reviewed_by,reviewed_at,corrected_data)
                VALUES (gen_random_uuid(), ?, 'CORRECTED', 'latest', ?, CURRENT_TIMESTAMP,
                  '{"defects":[{"defectType":"CRACK","severity":"SEVERE","quantity":2,"part":"外墙"},
                               {"defectType":"CRACK","severity":"SEVERE","quantity":3,"part":"外墙"}]}'::jsonb)
                """, taskId, userId);

        List<Map<String, Object>> evidence = repository.findEligibleAiEvidence(buildingId);

        assertThat(evidence).hasSize(1);
        assertThat(evidence.getFirst().get("defectType")).isEqualTo("CRACK");
        assertThat(evidence.getFirst().get("severity")).isEqualTo("SEVERE");
        assertThat(evidence.getFirst().get("quantity")).isEqualTo(5);
    }

    private UUID insertSucceededTask(String reviewStatus, String suffix) {
        UUID taskId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai.inference_task(id,request_code,asset_id,building_id,community_id,
                  model_registry_id,mode,status,attempt_no,review_status,requested_by,
                  requested_at,completed_at,duration_ms,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,10,0)
                """, taskId, "AI-ASSESS-" + suffix + "-" + taskId.toString().substring(0, 6),
                assetId, buildingId, communityId, modelRegistryId, "REAL", "SUCCEEDED", 1,
                reviewStatus, userId);
        return taskId;
    }

    private UUID insertResult(UUID taskId) {
        UUID resultId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai.inference_result(id,inference_task_id,image_width,image_height,
                  quality_status,applicability,summary,raw_output_snapshot,warning_messages)
                VALUES (?,?,64,64,'ACCEPTABLE','APPLICABLE','{"part":"外墙"}'::jsonb,'{}'::jsonb,'[]'::jsonb)
                """, resultId, taskId);
        return resultId;
    }

    private void insertDetection(UUID resultId, int sequenceNo, String classCode, String severity, String part) {
        jdbc.update("""
                INSERT INTO ai.detection(id,inference_result_id,sequence_no,class_code,class_name,
                  confidence,bbox_x,bbox_y,bbox_width,bbox_height,coordinate_type,extra_data)
                VALUES (gen_random_uuid(),?,?,?,?,0.8,0.1,0.1,0.2,0.2,'NORMALIZED_XYWH',
                  jsonb_build_object('severity', ?, 'part', ?))
                """, resultId, sequenceNo, classCode, classCode, severity, part);
    }
}
