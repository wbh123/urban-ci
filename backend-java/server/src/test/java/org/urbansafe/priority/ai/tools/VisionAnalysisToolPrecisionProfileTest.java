package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationService;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.security.AiVisionAssetAccessService;
import org.urbansafe.priority.asset.service.Phase2AssetService;

class VisionAnalysisToolPrecisionProfileTest {

    @BeforeEach
    void beginTrace() {
        AiAgentTrace.begin(new AiAgentExecution(
                UUID.randomUUID(), "AI_INFERENCE", UUID.randomUUID(),
                "精度视觉测试", UUID.randomUUID(), "t"));
    }

    @AfterEach
    void endTrace() {
        AiAgentTrace.end();
    }

    @Test
    void professionalVisionToolShouldExplicitlyRequestPrecisionProfile() {
        UUID assetId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        Phase2AssetService assetService = mock(Phase2AssetService.class);
        when(assetService.get(assetId)).thenReturn(Map.of("contentType", "image/jpeg"));
        when(assetService.content(assetId)).thenReturn(new byte[] {1, 2, 3});
        AiVisionAssetAccessService accessService = mock(AiVisionAssetAccessService.class);
        AiOrchestrationService orchestration = mock(AiOrchestrationService.class);
        when(orchestration.execute(any())).thenReturn(new AiStructuredResult(
                "req", "FAST_API", "AI-VISION-LOCAL-001", "1.1.0",
                AiCapabilityType.VISION_INFERENCE, "SUCCEEDED", "未发现高可信候选",
                List.of(), List.of(), List.of(), null, List.of(), "fast-api:req", 3000L));

        VisionAnalysisTool tool = new VisionAnalysisTool(assetService, accessService, orchestration);
        tool.analyze(assetId.toString(), buildingId.toString());

        ArgumentCaptor<AiOrchestrationRequest> captor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        org.mockito.Mockito.verify(orchestration).execute(captor.capture());
        assertThat(captor.getValue().inputs()).containsEntry("inferenceProfile", "PRECISION");
    }
}
