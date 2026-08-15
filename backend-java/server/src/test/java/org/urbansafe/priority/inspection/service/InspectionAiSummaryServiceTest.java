package org.urbansafe.priority.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.execution.AiAgentExecutionStatus;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.ai.service.AiInferenceService;

class InspectionAiSummaryServiceTest {

    @Test
    void combinesInspectorNotesWithExistingInferenceWithoutRequestingNewVisionRun() {
        Phase2InspectionService inspection = mock(Phase2InspectionService.class);
        AiInferenceService inference = mock(AiInferenceService.class);
        SpringAiOrchestrationService orchestration = mock(SpringAiOrchestrationService.class);
        InspectionAiSummaryService service = new InspectionAiSummaryService(inspection, inference, orchestration);

        UUID taskId = UUID.randomUUID();
        UUID inferenceId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(inspection.getTask(taskId)).thenReturn(Map.of(
                "taskId", taskId, "buildingId", buildingId, "title", "外墙巡检"));
        when(inspection.listRecords(taskId)).thenReturn(List.of(Map.of(
                "inspectionPart", "南侧外墙",
                "severity", "HIGH",
                "summary", "窗角可见裂缝并伴随雨后水迹",
                "rectificationSuggestion", "补拍裂缝近景")));
        when(inference.getDetail(inferenceId)).thenReturn(Map.of(
                "inferenceId", inferenceId,
                "inspectionTaskId", taskId,
                "buildingId", buildingId,
                "status", "SUCCEEDED",
                "detections", List.of(Map.of("className", "裂缝", "confidence", 0.82))));
        when(orchestration.runIntelligentAnalysis(
                eq("INSPECTION_SUMMARY"), eq(buildingId), anyString(),
                argThat(context -> context.containsKey("buildingId") && !context.containsKey("assetId")),
                eq(userId), eq("inspector")))
                .thenReturn(new SpringAiOrchestrationService.IntelligentAnalysisResult(
                        UUID.randomUUID(), AiAgentExecutionStatus.SUCCEEDED,
                        "现场描述：南侧外墙窗角裂缝并伴随水迹\n"
                                + "AI视觉发现：识别到疑似裂缝\n"
                                + "相互印证或冲突：两类证据均指向裂缝\n"
                                + "重点位置：南侧外墙\n"
                                + "建议补充证据：补拍带尺度参照近景\n"
                                + "人工复核建议：优先核实裂缝宽度和渗水范围",
                        List.of(), 80L, "deepseek-chat"));

        Map<String, Object> result = service.summarize(taskId, inferenceId, userId, "inspector");

        assertThat(result.get("mode")).isEqualTo("AI");
        assertThat(result.get("fieldDescription")).asString().contains("南侧外墙");
        assertThat(result.get("visualFindings")).asString().contains("裂缝");
        verify(orchestration).runIntelligentAnalysis(
                eq("INSPECTION_SUMMARY"), eq(buildingId), anyString(),
                argThat(context -> context.containsKey("buildingId") && !context.containsKey("assetId")),
                eq(userId), eq("inspector"));
    }

    @Test
    void returnsRuleFallbackWhenTextAiIsUnavailable() {
        Phase2InspectionService inspection = mock(Phase2InspectionService.class);
        AiInferenceService inference = mock(AiInferenceService.class);
        SpringAiOrchestrationService orchestration = mock(SpringAiOrchestrationService.class);
        InspectionAiSummaryService service = new InspectionAiSummaryService(inspection, inference, orchestration);

        UUID taskId = UUID.randomUUID();
        UUID inferenceId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        when(inspection.getTask(taskId)).thenReturn(Map.of("taskId", taskId, "buildingId", buildingId));
        when(inspection.listRecords(taskId)).thenReturn(List.of(Map.of(
                "inspectionPart", "东侧外墙", "severity", "MEDIUM", "summary", "发现水迹")));
        when(inference.getDetail(inferenceId)).thenReturn(Map.of(
                "inspectionTaskId", taskId, "buildingId", buildingId, "status", "SUCCEEDED",
                "detections", List.of(Map.of("className", "水渍", "confidence", 0.73))));
        when(orchestration.runIntelligentAnalysis(
                eq("INSPECTION_SUMMARY"), eq(buildingId), anyString(),
                argThat(context -> !context.containsKey("assetId")),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new SpringAiOrchestrationService.IntelligentAnalysisResult(
                        UUID.randomUUID(), AiAgentExecutionStatus.PARTIAL_SUCCEEDED,
                        "智能文本能力暂不可用", List.of(), 0L, null));

        Map<String, Object> result = service.summarize(
                taskId, inferenceId, UUID.randomUUID(), "inspector");

        assertThat(result.get("mode")).isEqualTo("RULE_FALLBACK");
        assertThat(result.get("fieldDescription")).asString().contains("发现水迹");
        assertThat(result.get("visualFindings")).asString().contains("水渍");
        assertThat(result.get("disclaimer")).asString().contains("不构成专业鉴定结论");
    }
}
