package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.provider.DifyWorkflowProvider;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

class DifyReviewAssistToolTest {

    private AiAgentExecution execution;

    @BeforeEach
    void beginTrace() {
        execution = new AiAgentExecution(
                UUID.randomUUID(), "BUILDING", UUID.randomUUID(), "综合分析", UUID.randomUUID(), "t");
        AiAgentTrace.begin(execution);
    }

    @AfterEach
    void endTrace() {
        AiAgentTrace.end();
    }

    @Test
    void assemblesPublishedDslInputsFromAuthorizedBusinessData() {
        Fixture fixture = new Fixture();
        fixture.stubSuccess();

        fixture.tool.run(fixture.buildingId.toString());

        ArgumentCaptor<AiOrchestrationRequest> captor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(fixture.provider).execute(captor.capture());
        AiOrchestrationRequest request = captor.getValue();
        assertThat(request.modelCode()).isEqualTo("DIFY-REVIEW-ASSIST-001");
        assertThat(request.inputs()).containsOnlyKeys(
                "analysisJson", "inspectionRecordJson", "localModelJson", "buildingContextJson");
        assertThat(String.valueOf(request.inputs().get("buildingContextJson")))
                .contains("测试楼", fixture.buildingId.toString());
        assertThat(String.valueOf(request.inputs().get("inspectionRecordJson")))
                .contains("inspectionTaskCount", "inspectionRecordCount");
        assertThat(String.valueOf(request.inputs().get("analysisJson")))
                .contains(fixture.inferenceId.toString(), "CRACK");
        verify(fixture.accessService).assertCanReadBuilding(fixture.buildingId);
    }

    @Test
    void boundSourceInferenceBypassesBuildingLatestSeedResult() throws Exception {
        Fixture fixture = new Fixture();
        fixture.stubSuccess();
        UUID boundSourceInferenceId = UUID.randomUUID();
        AiAgentTrace.begin(execution, Map.of("sourceInferenceId", boundSourceInferenceId.toString()));
        when(fixture.inferenceService.getDetail(boundSourceInferenceId)).thenReturn(Map.of(
                "inferenceId", boundSourceInferenceId,
                "buildingId", fixture.buildingId,
                "assetId", UUID.randomUUID(),
                "status", "SUCCEEDED",
                "mode", "REAL",
                "modelId", "AI-VISION-LOCAL-001",
                "detectionCount", 2,
                "detections", List.of(
                        Map.of("classCode", "CRACK", "confidence", 0.47),
                        Map.of("classCode", "CRACK", "confidence", 0.44))));

        fixture.tool.run(fixture.buildingId.toString());

        ArgumentCaptor<AiOrchestrationRequest> captor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(fixture.provider).execute(captor.capture());
        JsonNode analysis = new ObjectMapper().readTree(
                String.valueOf(captor.getValue().inputs().get("analysisJson")));
        JsonNode buildingContext = new ObjectMapper().readTree(
                String.valueOf(captor.getValue().inputs().get("buildingContextJson")));
        assertThat(analysis.path("inferenceId").asText()).isEqualTo(boundSourceInferenceId.toString());
        assertThat(analysis.path("detectionCount").asInt()).isEqualTo(2);
        assertThat(buildingContext.path("sourceInferenceId").asText())
                .isEqualTo(boundSourceInferenceId.toString());
        verify(fixture.inferenceService, never()).list(any(), eq(0), eq(1));
    }

    @Test
    void keepsDetectionCountAlignedWhenStructuredProjectionIsEmpty() throws Exception {
        Fixture fixture = new Fixture();
        fixture.stubSuccess();
        when(fixture.inferenceService.getDetail(fixture.inferenceId)).thenReturn(Map.of(
                "inferenceId", fixture.inferenceId,
                "status", "SUCCEEDED",
                "mode", "REAL",
                "modelId", "AI-VISION-LOCAL-001",
                "detectionCount", 0,
                "detections", List.of(Map.of("classCode", "CRACK", "confidence", 0.31)),
                "structuredResult", Map.of("detections", List.of())));

        fixture.tool.run(fixture.buildingId.toString());

        ArgumentCaptor<AiOrchestrationRequest> captor = ArgumentCaptor.forClass(AiOrchestrationRequest.class);
        verify(fixture.provider).execute(captor.capture());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode analysis = mapper.readTree(String.valueOf(captor.getValue().inputs().get("analysisJson")));
        JsonNode localModel = mapper.readTree(String.valueOf(captor.getValue().inputs().get("localModelJson")));
        assertThat(analysis.path("detections").size()).isEqualTo(1);
        assertThat(analysis.path("detectionCount").asInt()).isEqualTo(1);
        assertThat(localModel.path("detections").size()).isEqualTo(1);
        assertThat(localModel.path("detectionCount").asInt()).isEqualTo(1);
    }

    @Test
    void returnsUnavailableWhenDifyActuallyFailsButKeepsTraceAuditable() {
        Fixture fixture = new Fixture();
        fixture.stubBusinessData();
        when(fixture.provider.execute(any())).thenThrow(new AiProviderException(
                AiErrorCodes.AI_WORKFLOW_FAILED, "Dify 节点执行失败 runId=run-1"));

        DifyReviewAssistTool.DifyToolResult result = fixture.tool.run(fixture.buildingId.toString());

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.summary()).contains("runId=run-1");
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("DifyReviewAssistTool")
                        && step.status() == AiAgentStepStatus.FAILED
                        && AiErrorCodes.AI_WORKFLOW_FAILED.equals(step.errorCode()));
    }

    private static final class Fixture {
        final UUID buildingId = UUID.randomUUID();
        final UUID inferenceId = UUID.randomUUID();
        final UUID inspectionTaskId = UUID.randomUUID();
        final DifyWorkflowProvider provider = mock(DifyWorkflowProvider.class);
        final BusinessAccessService accessService = mock(BusinessAccessService.class);
        final BuildingService buildingService = mock(BuildingService.class);
        final Phase2InspectionService inspectionService = mock(Phase2InspectionService.class);
        final AiInferenceService inferenceService = mock(AiInferenceService.class);
        final DifyReviewAssistTool tool = new DifyReviewAssistTool(
                provider, accessService, buildingService, inspectionService,
                inferenceService, new ObjectMapper());

        void stubBusinessData() {
            when(buildingService.getBuilding(buildingId)).thenReturn(new BuildingDetailResult(
                    buildingId, UUID.randomUUID(), "B001", "测试楼", "武汉市测试地址",
                    2000, "FRAME", 8, new BigDecimal("5000"), 56, 146, 10, 12,
                    false, false, false, new BigDecimal("77"), "ACTIVE",
                    null, null, null, null, 1L));
            when(inspectionService.listTasks(buildingId, null)).thenReturn(List.of(
                    Map.of("taskId", inspectionTaskId)));
            when(inspectionService.listRecords(inspectionTaskId)).thenReturn(List.of(
                    Map.of("recordId", UUID.randomUUID()), Map.of("recordId", UUID.randomUUID())));
            when(inferenceService.list(any(), eq(0), eq(1)))
                    .thenReturn(Map.of("content", List.of(Map.of("inferenceId", inferenceId))));
            when(inferenceService.getDetail(inferenceId)).thenReturn(Map.of(
                    "inferenceId", inferenceId,
                    "status", "SUCCEEDED",
                    "mode", "REAL",
                    "modelId", "AI-VISION-LOCAL-001",
                    "detections", List.of(Map.of("classCode", "CRACK", "confidence", 0.31))));
        }

        void stubSuccess() {
            stubBusinessData();
            when(provider.execute(any())).thenReturn(new AiOrchestrationResult(
                    "req", "DIFY", "DIFY-REVIEW-ASSIST-001", "review-assist-v1.2.0", null,
                    "SUCCEEDED", "复核辅助完成", List.of(), List.of(),
                    List.of("补拍裂缝近景"), 0.8d, List.of(), "dify:run-1", 100L));
        }
    }
}
