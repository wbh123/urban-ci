package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

class FeedbackClosureServiceTest {

    private FeedbackRepository repository;
    private FeedbackClosureRepository closureRepository;
    private Phase2InspectionService inspectionService;
    private FeedbackService feedbackService;
    private FeedbackClosureService service;

    @BeforeEach
    void setUp() {
        repository = mock(FeedbackRepository.class);
        closureRepository = mock(FeedbackClosureRepository.class);
        inspectionService = mock(Phase2InspectionService.class);
        feedbackService = mock(FeedbackService.class);
        service = new FeedbackClosureService(repository, closureRepository, inspectionService, feedbackService);
    }

    @Test
    void resolvedRectificationCreatesReinspectionTaskAndLinksItByEvent() {
        UUID reportId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-001",
                "status", "RESOLVED",
                "buildingId", buildingId)));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.empty());
        when(inspectionService.createTask(anyMap())).thenReturn(Map.of(
                "taskId", taskId,
                "taskCode", "IT-RECHECK-001",
                "buildingId", buildingId,
                "inspectionType", "REINSPECTION",
                "status", "PENDING"));

        Map<String, Object> result = service.createReinspection(reportId, actor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> taskBody = ArgumentCaptor.forClass(Map.class);
        verify(inspectionService).createTask(taskBody.capture());
        assertThat(taskBody.getValue()).containsEntry("buildingId", buildingId.toString());
        assertThat(taskBody.getValue()).containsEntry("inspectionType", "REINSPECTION");
        verify(repository).insertEvent(
                eq(reportId), eq("REINSPECTION_CREATED"), eq("RESOLVED"), eq("RESOLVED"),
                eq("整改已完成，已安排复查复验。"), eq("PUBLIC"), eq("STAFF"), eq(actor), anyMap());
        assertThat(result).containsEntry("taskId", taskId);
        assertThat(result).containsEntry("reused", false);
    }

    @Test
    void completedReinspectionCanCloseTheFeedbackLoop() {
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-001",
                "status", "RESOLVED",
                "buildingId", UUID.randomUUID())));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.of(Map.of(
                "taskId", taskId,
                "taskCode", "IT-RECHECK-001",
                "status", "COMPLETED")));
        when(feedbackService.updateStatus(eq(reportId), anyMap(), eq(actor)))
                .thenReturn(Map.of("reportId", reportId, "status", "CLOSED"));

        Map<String, Object> result = service.completeReinspection(
                reportId, true, "复查未发现原整改问题继续存在，复验通过。", actor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> statusBody = ArgumentCaptor.forClass(Map.class);
        verify(feedbackService).updateStatus(eq(reportId), statusBody.capture(), eq(actor));
        assertThat(statusBody.getValue()).containsEntry("status", "CLOSED");
        verify(repository).insertEvent(
                eq(reportId), eq("REINSPECTION_PASSED"), eq("RESOLVED"), eq("CLOSED"),
                eq("复查复验通过，整改事项已闭环。"), eq("PUBLIC"), eq("STAFF"), eq(actor), anyMap());
        assertThat(result).containsEntry("status", "CLOSED");
        assertThat(result).containsEntry("taskId", taskId);
    }

    @Test
    void failedReinspectionReturnsToProcessingInsteadOfClosing() {
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-001",
                "status", "RESOLVED",
                "buildingId", UUID.randomUUID())));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.of(Map.of(
                "taskId", taskId,
                "taskCode", "IT-RECHECK-001",
                "status", "COMPLETED")));
        when(feedbackService.updateStatus(eq(reportId), anyMap(), eq(actor)))
                .thenReturn(Map.of("reportId", reportId, "status", "PROCESSING"));

        Map<String, Object> result = service.completeReinspection(
                reportId, false, "原位置仍有松动，需要继续整改。", actor);

        assertThat(result).containsEntry("status", "PROCESSING");
        verify(repository).insertEvent(
                eq(reportId), eq("REINSPECTION_FAILED"), eq("RESOLVED"), eq("PROCESSING"),
                eq("复查复验未通过，已退回继续整改。"), eq("PUBLIC"), eq("STAFF"), eq(actor), anyMap());
    }

    @Test
    void reinspectionResultCannotBeSubmittedBeforeTaskCompletion() {
        UUID reportId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-001",
                "status", "RESOLVED",
                "buildingId", UUID.randomUUID())));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.of(Map.of(
                "taskId", taskId,
                "taskCode", "IT-RECHECK-001",
                "status", "IN_PROGRESS")));

        assertThatThrownBy(() -> service.completeReinspection(
                reportId, true, "提前提交", UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("复查任务完成");
    }
}
