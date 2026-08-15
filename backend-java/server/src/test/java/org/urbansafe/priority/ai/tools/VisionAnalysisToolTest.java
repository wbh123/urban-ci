package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationService;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.security.AiVisionAssetAccessService;
import org.urbansafe.priority.asset.service.Phase2AssetService;

/** 本地视觉 Tool 测试：权限/输入/底层调用/异常转换。 */
class VisionAnalysisToolTest {

    private AiAgentExecution execution;

    @BeforeEach
    void beginTrace() {
        execution = new AiAgentExecution(
                UUID.randomUUID(), "AI_INFERENCE", UUID.randomUUID(), "分析", UUID.randomUUID(), "t");
        AiAgentTrace.begin(execution);
    }

    @AfterEach
    void endTrace() {
        AiAgentTrace.end();
    }

    @Test
    void returnsStructuredDetectionsOnSuccess() {
        UUID assetId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        Phase2AssetService assetService = mock(Phase2AssetService.class);
        when(assetService.get(assetId)).thenReturn(Map.of("contentType", "image/png"));
        when(assetService.content(assetId)).thenReturn(new byte[] {1, 2, 3});
        AiVisionAssetAccessService assetAccess = mock(AiVisionAssetAccessService.class);
        AiOrchestrationService orchestration = mock(AiOrchestrationService.class);
        when(orchestration.execute(any())).thenReturn(new AiStructuredResult(
                "req", "FAST_API", "AI-VISION-LOCAL-001", "1.0.0",
                AiCapabilityType.VISION_INFERENCE, "SUCCEEDED", "疑似裂缝",
                List.of(new AiStructuredResult.Detection(
                        "CRACK", "疑似裂缝", 0.3d, null, null)),
                List.of(), List.of(), 0.3d, List.of(), "fast-api:req", 400L));

        VisionAnalysisTool tool = new VisionAnalysisTool(assetService, assetAccess, orchestration);
        VisionAnalysisTool.VisionToolResult result = tool.analyze(assetId.toString(), buildingId.toString());

        verify(assetAccess).assertCanReadAssetForBuilding(assetId, buildingId);
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.detectionCount()).isEqualTo(1);
        assertThat(result.modelCode()).isEqualTo("AI-VISION-LOCAL-001");
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("VisionAnalysisTool")
                        && step.status() == AiAgentStepStatus.SUCCEEDED);
    }

    @Test
    void propagatesObjectLevelAccessDenialBeforeReadingContent() {
        UUID assetId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        Phase2AssetService assetService = mock(Phase2AssetService.class);
        AiVisionAssetAccessService assetAccess = mock(AiVisionAssetAccessService.class);
        org.mockito.Mockito.doThrow(new AccessDeniedException("AI_VISION_ASSET_BUILDING_MISMATCH"))
                .when(assetAccess).assertCanReadAssetForBuilding(assetId, buildingId);
        AiOrchestrationService orchestration = mock(AiOrchestrationService.class);

        VisionAnalysisTool tool = new VisionAnalysisTool(assetService, assetAccess, orchestration);

        assertThatThrownBy(() -> tool.analyze(assetId.toString(), buildingId.toString()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("AI_VISION_ASSET_BUILDING_MISMATCH");
        verify(assetService, never()).get(assetId);
        verify(assetService, never()).content(assetId);
        verify(orchestration, never()).execute(any());
    }

    @Test
    void returnsFailedResultWithoutThrowingWhenVisionUnavailable() {
        UUID assetId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        Phase2AssetService assetService = mock(Phase2AssetService.class);
        when(assetService.get(assetId)).thenReturn(Map.of("contentType", "image/png"));
        when(assetService.content(assetId)).thenReturn(new byte[] {1, 2, 3});
        AiVisionAssetAccessService assetAccess = mock(AiVisionAssetAccessService.class);
        AiOrchestrationService orchestration = mock(AiOrchestrationService.class);
        when(orchestration.execute(any())).thenThrow(new AiProviderException(
                AiErrorCodes.AI_PROVIDER_UNAVAILABLE, "视觉服务暂时不可用"));

        VisionAnalysisTool tool = new VisionAnalysisTool(assetService, assetAccess, orchestration);
        VisionAnalysisTool.VisionToolResult result = tool.analyze(assetId.toString(), buildingId.toString());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.disclaimer()).contains("未获得实时视觉分析结果");
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("VisionAnalysisTool")
                        && step.status() == AiAgentStepStatus.FAILED);
    }
}
