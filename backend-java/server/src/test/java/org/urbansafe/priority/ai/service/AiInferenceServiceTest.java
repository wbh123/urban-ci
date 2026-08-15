package org.urbansafe.priority.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.command.CreateInferenceCommand;
import org.urbansafe.priority.ai.command.RetryCommand;
import org.urbansafe.priority.ai.command.ReviewCommand;
import org.urbansafe.priority.ai.config.AiInferenceProperties;
import org.urbansafe.priority.ai.provider.AiInferenceProvider;
import org.urbansafe.priority.ai.repository.AiInferenceRepository;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

/**
 * 人工智能推理编排服务单元测试，使用 Mockito 隔离持久层与统一模型提供者。
 */
@ExtendWith(MockitoExtension.class)
class AiInferenceServiceTest {

    private static final UUID ASSET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BUILDING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COMMUNITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MODEL_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Mock private AiInferenceRepository repository;
    @Mock private AiInferenceProvider inferenceProvider;
    @Mock private Phase2AssetService assetService;
    @Mock private AuditService auditService;

    private AiInferenceProperties properties;

    @InjectMocks
    private AiInferenceService service;

    @BeforeEach
    void setUp() {
        properties = new AiInferenceProperties();
        properties.setDefaultMockModelId("AI-DEFECT-MOCK-001");
        properties.setDefaultMode("MOCK");
        service = new AiInferenceService(repository, inferenceProvider, assetService, auditService, properties);
    }

    @Test
    void createShouldSucceedWhenProviderReturnsDetections() {
        stubAssetAndTraceability();
        stubMockModel();
        AiInferenceResponse response = buildResponse("SUCCEEDED", "APPLICABLE", 1);
        when(inferenceProvider.infer(any(), any(), anyString())).thenReturn(response);
        when(repository.markRunning(any())).thenReturn(1);
        when(repository.findTaskDetail(any())).thenReturn(Optional.of(
                Map.of("inferenceId", UUID.randomUUID(), "status", "SUCCEEDED")));

        Map<String, Object> result = service.create(new CreateInferenceCommand(
                ASSET_ID, "MOCK", null, "key-1", USER_ID));

        verify(repository).insertTask(any(), anyString(), anyString(), eq(ASSET_ID), any(), any(),
                eq(BUILDING_ID), eq(COMMUNITY_ID), eq(MODEL_ID), eq("MOCK"), eq(1), eq(USER_ID));
        verify(repository).saveSuccess(any(), eq(response));
        verify(repository, never()).saveFailure(any(), any(), any(), anyBoolean());
        assertEquals("SUCCEEDED", result.get("status"));
    }

    @Test
    void createShouldFailTaskWhenProviderTimesOut() {
        stubAssetAndTraceability();
        stubMockModel();
        when(inferenceProvider.infer(any(), any(), anyString()))
                .thenThrow(new AiFastApiException("AI_SERVICE_TIMEOUT", "FastAPI 调用超时"));
        when(repository.markRunning(any())).thenReturn(1);
        when(repository.isImageRejection("AI_SERVICE_TIMEOUT")).thenReturn(false);
        when(repository.findTaskDetail(any())).thenReturn(Optional.of(
                Map.of("inferenceId", UUID.randomUUID(), "status", "FAILED")));

        Map<String, Object> result = service.create(new CreateInferenceCommand(
                ASSET_ID, "MOCK", null, null, USER_ID));

        verify(repository).saveFailure(any(), eq("AI_SERVICE_TIMEOUT"), eq("模型服务调用超时"), eq(false));
        verify(repository, never()).saveSuccess(any(), any());
        assertEquals("FAILED", result.get("status"));
    }

    @Test
    void createShouldRejectTaskWhenImageDecodeFails() {
        stubAssetAndTraceability();
        stubMockModel();
        when(inferenceProvider.infer(any(), any(), anyString()))
                .thenThrow(new AiFastApiException("AI_IMAGE_DECODE_FAILED", "损坏"));
        when(repository.markRunning(any())).thenReturn(1);
        when(repository.isImageRejection("AI_IMAGE_DECODE_FAILED")).thenReturn(true);
        when(repository.findTaskDetail(any())).thenReturn(Optional.of(
                Map.of("inferenceId", UUID.randomUUID(), "status", "REJECTED")));

        Map<String, Object> result = service.create(new CreateInferenceCommand(
                ASSET_ID, "MOCK", null, null, USER_ID));

        verify(repository).saveFailure(any(), eq("AI_IMAGE_DECODE_FAILED"), anyString(), eq(true));
        assertEquals("REJECTED", result.get("status"));
    }

    @Test
    void createShouldRejectWhenImageNotApplicable() {
        stubAssetAndTraceability();
        stubMockModel();
        AiInferenceResponse response = buildResponse("REJECTED", "NOT_APPLICABLE", 0);
        when(inferenceProvider.infer(any(), any(), anyString())).thenReturn(response);
        when(repository.markRunning(any())).thenReturn(1);
        when(repository.findTaskDetail(any())).thenReturn(Optional.of(
                Map.of("inferenceId", UUID.randomUUID(), "status", "REJECTED")));

        service.create(new CreateInferenceCommand(ASSET_ID, "MOCK", null, null, USER_ID));

        verify(repository).saveSuccess(any(), eq(response));
        verify(repository).saveFailure(any(), eq("AI_IMAGE_NOT_APPLICABLE"), anyString(), eq(true));
    }

    @Test
    void createShouldReturnExistingTaskOnIdempotencyDuplicate() {
        stubAssetAndTraceability();
        stubMockModel();
        org.springframework.dao.DuplicateKeyException duplicate =
                new org.springframework.dao.DuplicateKeyException("idempotency");
        org.mockito.Mockito.doThrow(duplicate).when(repository)
                .insertTask(any(), anyString(), anyString(), eq(ASSET_ID), any(), any(),
                        eq(BUILDING_ID), eq(COMMUNITY_ID), eq(MODEL_ID), eq("MOCK"), eq(1), eq(USER_ID));
        UUID existingId = UUID.randomUUID();
        when(repository.findActiveTask(eq(USER_ID), eq(ASSET_ID), eq("MOCK"), eq(MODEL_ID), eq("key-1")))
                .thenReturn(Optional.of(Map.of("inferenceId", existingId)));
        when(repository.findTaskDetail(existingId))
                .thenReturn(Optional.of(Map.of("inferenceId", existingId, "status", "RUNNING")));

        Map<String, Object> result = service.create(new CreateInferenceCommand(
                ASSET_ID, "MOCK", null, "key-1", USER_ID));

        assertEquals(existingId, result.get("inferenceId"));
        verify(repository, never()).saveSuccess(any(), any());
    }

    @Test
    void createShouldRejectWhenAssetIsNotImage() {
        when(assetService.get(ASSET_ID)).thenReturn(Map.of("contentType", "application/pdf"));
        assertThrows(InvalidRequestException.class, () -> service.create(
                new CreateInferenceCommand(ASSET_ID, "MOCK", null, null, USER_ID)));
    }

    @Test
    void createShouldRejectWhenAssetNotFound() {
        when(assetService.get(ASSET_ID)).thenThrow(new ResourceNotFoundException("ASSET_NOT_FOUND", "图片不存在"));
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.create(
                new CreateInferenceCommand(ASSET_ID, "MOCK", null, null, USER_ID)));
        assertEquals("AI_ASSET_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void createShouldRejectRealModeWhenModelNotApproved() {
        stubImageAsset();
        when(repository.findModelByCode("AI-REAL-001")).thenReturn(Optional.of(Map.of(
                "id", MODEL_ID, "mode", "REAL", "status", "CANDIDATE")));

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () -> service.create(
                new CreateInferenceCommand(ASSET_ID, "REAL", "AI-REAL-001", null, USER_ID)));
        assertEquals("AI_MODEL_NOT_APPROVED", ex.getErrorCode());
    }

    @Test
    void retryShouldRejectNonFailedTask() {
        when(repository.findTaskStatus(any())).thenReturn(Optional.of(Map.of(
                "status", "SUCCEEDED", "assetId", ASSET_ID, "mode", "MOCK",
                "modelRegistryId", MODEL_ID)));
        assertThrows(ResourceConflictException.class, () -> service.retry(
                new RetryCommand(UUID.randomUUID(), null, USER_ID)));
    }

    @Test
    void reviewShouldRejectUnfinishedTask() {
        when(repository.findTaskStatus(any())).thenReturn(Optional.of(Map.of("status", "RUNNING")));
        assertThrows(ResourceConflictException.class, () -> service.review(
                new ReviewCommand(UUID.randomUUID(), "CONFIRMED", "ok", USER_ID)));
    }

    @Test
    void reviewShouldSaveReviewForSucceededTask() {
        when(repository.findTaskStatus(any())).thenReturn(Optional.of(Map.of("status", "SUCCEEDED")));
        Map<String, Object> result = service.review(
                new ReviewCommand(UUID.randomUUID(), "CONFIRMED", "确认", USER_ID));
        verify(repository).saveReview(any(), eq("CONFIRMED"), eq("确认"), eq(USER_ID));
        assertEquals("CONFIRMED", result.get("reviewStatus"));
    }

    private void stubImageAsset() {
        when(assetService.get(ASSET_ID)).thenReturn(Map.of("contentType", "image/jpeg"));
    }

    private void stubAssetAndTraceability() {
        stubImageAsset();
        Map<String, Object> traceability = new LinkedHashMap<>();
        traceability.put("buildingId", BUILDING_ID);
        traceability.put("communityId", COMMUNITY_ID);
        traceability.put("inspectionTaskId", null);
        traceability.put("inspectionRecordId", null);
        when(repository.resolveAssetTraceability(ASSET_ID)).thenReturn(Optional.of(traceability));
        when(assetService.content(ASSET_ID)).thenReturn(new byte[]{1, 2, 3});
        org.mockito.Mockito.lenient()
                .when(inferenceProvider.buildMetadata(
                        anyString(), anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(Map.of("mode", "MOCK", "requestedModelId", "AI-DEFECT-MOCK-001"));
    }

    private void stubMockModel() {
        when(repository.findModelByCode("AI-DEFECT-MOCK-001")).thenReturn(Optional.of(Map.of(
                "id", MODEL_ID, "mode", "MOCK", "status", "MOCK",
                "modelCode", "AI-DEFECT-MOCK-001", "modelVersion", "0.1.0")));
    }

    private AiInferenceResponse buildResponse(String status, String applicability, int detectionCount) {
        List<AiInferenceResponse.Detection> detections = new java.util.ArrayList<>();
        for (int i = 0; i < detectionCount; i++) {
            detections.add(new AiInferenceResponse.Detection(
                    i + 1, "CRACK", "裂缝", 0.82,
                    new AiInferenceResponse.BoundingBox(0.1, 0.1, 0.2, 0.2, "NORMALIZED_XYWH"),
                    null));
        }
        return new AiInferenceResponse(
                "UNKNOWN", status, "MOCK",
                new AiInferenceResponse.ModelBrief("AI-DEFECT-MOCK-001", "Mock", "0.1.0"),
                new AiInferenceResponse.ImageInfo(64, 64, "ACCEPTABLE", applicability),
                detections,
                new AiInferenceResponse.Summary(detectionCount, Map.of("CRACK", detectionCount)),
                12L,
                List.of("模拟结果仅用于业务链路验证"));
    }
}
