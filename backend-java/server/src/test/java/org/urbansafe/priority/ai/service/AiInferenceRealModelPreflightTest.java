package org.urbansafe.priority.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.command.CreateInferenceCommand;
import org.urbansafe.priority.ai.config.AiInferenceProperties;
import org.urbansafe.priority.ai.provider.AiInferenceProvider;
import org.urbansafe.priority.ai.repository.AiInferenceRepository;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.audit.service.AuditService;

/** 验证业务层在 REAL 推理前只通过统一提供者检查指定模型就绪状态。 */
@ExtendWith(MockitoExtension.class)
class AiInferenceRealModelPreflightTest {

    private static final UUID ASSET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BUILDING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID COMMUNITY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID MODEL_REGISTRY_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String MODEL_CODE = "AI-REAL-001";

    @Mock private AiInferenceRepository repository;
    @Mock private AiInferenceProvider inferenceProvider;
    @Mock private Phase2AssetService assetService;
    @Mock private AuditService auditService;

    private AiInferenceService service;

    @BeforeEach
    void setUp() {
        AiInferenceProperties properties = new AiInferenceProperties();
        properties.setDefaultMode("MOCK");
        service = new AiInferenceService(repository, inferenceProvider, assetService, auditService, properties);
    }

    @Test
    void realInferenceShouldRequireExactModelBeforeInfer() {
        when(assetService.get(ASSET_ID)).thenReturn(Map.of("contentType", "image/jpeg"));
        when(assetService.content(ASSET_ID)).thenReturn(new byte[]{1, 2, 3});
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("buildingId", BUILDING_ID);
        trace.put("communityId", COMMUNITY_ID);
        trace.put("inspectionTaskId", null);
        trace.put("inspectionRecordId", null);
        when(repository.resolveAssetTraceability(ASSET_ID)).thenReturn(Optional.of(trace));
        when(repository.findModelByCode(MODEL_CODE)).thenReturn(Optional.of(Map.of(
                "id", MODEL_REGISTRY_ID,
                "mode", "REAL",
                "status", "APPROVED",
                "modelCode", MODEL_CODE,
                "modelVersion", "1.0.0")));
        when(repository.markRunning(any())).thenReturn(1);
        when(inferenceProvider.buildMetadata(anyString(), eq("REAL"), anyString(), anyString(), any(), any(), eq(MODEL_CODE)))
                .thenReturn(Map.of("mode", "REAL", "requestedModelId", MODEL_CODE));
        AiInferenceResponse response = new AiInferenceResponse(
                "UNKNOWN", "SUCCEEDED", "REAL",
                new AiInferenceResponse.ModelBrief(MODEL_CODE, "Real", "1.0.0"),
                new AiInferenceResponse.ImageInfo(64, 64, "ACCEPTABLE", "NO_DEFECT_FOUND"),
                List.of(), new AiInferenceResponse.Summary(0, Map.of()), 12L, List.of());
        when(inferenceProvider.infer(any(), any(), anyString())).thenReturn(response);
        when(repository.findTaskDetail(any())).thenReturn(Optional.of(
                Map.of("inferenceId", UUID.randomUUID(), "status", "SUCCEEDED")));

        service.create(new CreateInferenceCommand(ASSET_ID, "REAL", MODEL_CODE, "real-1", USER_ID));

        verify(inferenceProvider).requireModelReady(MODEL_CODE, "REAL");
        verify(inferenceProvider).infer(any(), any(), anyString());
        verify(repository).saveSuccess(any(), eq(response));
    }
}
