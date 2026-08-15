package org.urbansafe.priority.feedback.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.ai.execution.AiAgentExecutionStatus;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.feedback.repository.FeedbackAiAssistQueryRepository;

class FeedbackAiAssistServiceTest {

    @Test
    void analyzesServerSideFeedbackWithoutChangingBusinessState() {
        FeedbackAiAssistQueryRepository repository = mock(FeedbackAiAssistQueryRepository.class);
        SpringAiOrchestrationService orchestration = mock(SpringAiOrchestrationService.class);
        FeedbackAiAssistService service = new FeedbackAiAssistService(repository, orchestration);

        UUID reportId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> row = feedbackRow(reportId, buildingId);
        when(repository.findReport(reportId)).thenReturn(Optional.of(row));
        when(orchestration.runIntelligentAnalysis(
                eq("FEEDBACK"), eq(buildingId), org.mockito.ArgumentMatchers.anyString(), anyMap(),
                eq(userId), eq("manager")))
                .thenReturn(new SpringAiOrchestrationService.IntelligentAnalysisResult(
                        UUID.randomUUID(), AiAgentExecutionStatus.SUCCEEDED,
                        "初步类别：外墙裂缝\n建议关联对象：3号楼\n建议动作：安排巡检\n需人工确认：裂缝宽度与渗水范围",
                        List.of(), 120L, "deepseek-chat"));

        Map<String, Object> result = service.analyze(reportId, userId, "manager");

        assertEquals(reportId, result.get("reportId"));
        assertEquals("SUCCEEDED", result.get("status"));
        assertEquals(false, result.get("fallback"));
        assertTrue(String.valueOf(result.get("answer")).contains("建议动作"));

        ArgumentCaptor<String> question = ArgumentCaptor.forClass(String.class);
        verify(orchestration).runIntelligentAnalysis(
                eq("FEEDBACK"), eq(buildingId), question.capture(), anyMap(), eq(userId), eq("manager"));
        assertTrue(question.getValue().contains("WALL_CRACK"));
        assertTrue(question.getValue().contains("HIGH"));
        assertTrue(question.getValue().contains("外墙窗角出现新裂缝"));
        assertTrue(question.getValue().contains("3号楼东立面二层"));
        assertTrue(question.getValue().contains("初步类别"));
        assertTrue(question.getValue().contains("建议关联对象"));
        assertTrue(question.getValue().contains("建议动作"));
        assertTrue(question.getValue().contains("需人工确认"));
        assertTrue(question.getValue().contains("不得自动修改反馈状态"));
    }

    @Test
    void fallsBackToDeterministicClassificationWhenTextAiIsUnavailable() {
        FeedbackAiAssistQueryRepository repository = mock(FeedbackAiAssistQueryRepository.class);
        SpringAiOrchestrationService orchestration = mock(SpringAiOrchestrationService.class);
        FeedbackAiAssistService service = new FeedbackAiAssistService(repository, orchestration);

        UUID reportId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(feedbackRow(reportId, buildingId)));
        when(orchestration.runIntelligentAnalysis(
                eq("FEEDBACK"), eq(buildingId), org.mockito.ArgumentMatchers.anyString(), anyMap(),
                eq(userId), eq("manager")))
                .thenThrow(new IllegalStateException("DeepSeek unavailable"));

        Map<String, Object> result = service.analyze(reportId, userId, "manager");

        assertEquals("PARTIAL_SUCCEEDED", result.get("status"));
        assertEquals(true, result.get("fallback"));
        assertEquals("疑似外墙裂缝/渗水问题", result.get("category"));
        assertEquals("3号楼", result.get("relatedObject"));
        assertEquals("安排巡检", result.get("recommendedAction"));
        assertTrue(String.valueOf(result.get("basis")).contains("裂缝"));
        assertTrue(String.valueOf(result.get("answer")).contains("需人工确认"));
    }

    private static Map<String, Object> feedbackRow(UUID reportId, UUID buildingId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportId", reportId);
        row.put("reportCode", "FB-20260814-001");
        row.put("reportType", "WALL_CRACK");
        row.put("urgency", "HIGH");
        row.put("description", "外墙窗角出现新裂缝，雨后有渗水痕迹。");
        row.put("locationText", "3号楼东立面二层");
        row.put("buildingId", buildingId);
        row.put("buildingName", "3号楼");
        row.put("communityName", "测试小区");
        return row;
    }
}
