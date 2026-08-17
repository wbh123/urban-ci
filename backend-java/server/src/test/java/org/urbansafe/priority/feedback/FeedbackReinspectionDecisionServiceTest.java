package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

class FeedbackReinspectionDecisionServiceTest {

    private FeedbackRepository repository;
    private FeedbackClosureRepository closureRepository;
    private Phase2AssetService assetService;
    private FeedbackService feedbackService;
    private FeedbackClosureService service;

    @BeforeEach
    void setUp() {
        repository = mock(FeedbackRepository.class);
        closureRepository = mock(FeedbackClosureRepository.class);
        assetService = mock(Phase2AssetService.class);
        feedbackService = mock(FeedbackService.class);
        service = new FeedbackClosureService(
                repository,
                closureRepository,
                mock(Phase2InspectionService.class),
                assetService,
                feedbackService);
    }

    @Test
    void urgentStructuralFeedbackRecommendsReinspection() {
        UUID reportId = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "WALL_CRACK", "URGENT", "外墙裂缝持续扩大", "三层外墙")));

        Map<String, Object> result = service.recommendReinspection(reportId);

        assertThat(result)
                .containsEntry("recommendedDecision", "REQUIRED")
                .containsEntry("source", "STRUCTURED_RULES")
                .containsEntry("formalRiskChanged", false);
        assertThat((List<?>) result.get("reasons")).isNotEmpty();
    }

    @Test
    void ordinaryLowRiskFeedbackCanRecommendWaiver() {
        UUID reportId = UUID.randomUUID();
        when(repository.findReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "OTHER", "NORMAL", "公共区域标识脱胶需要重新粘贴", "一层入口")));

        Map<String, Object> result = service.recommendReinspection(reportId);

        assertThat(result).containsEntry("recommendedDecision", "WAIVED");
    }

    @Test
    void legacyRectificationSubmissionStillDefaultsToRequired() {
        UUID reportId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "OTHER", "NORMAL", "公共区域标识脱胶需要重新粘贴", "一层入口")));
        when(assetService.list("RESIDENT_REPORT", reportId)).thenReturn(rectificationEvidence());
        when(feedbackService.updateStatus(eq(reportId), anyMap(), eq(actor)))
                .thenReturn(Map.of("reportId", reportId, "status", "RESOLVED"));

        Map<String, Object> result = service.submitRectification(
                reportId, "已重新固定标识并完成清理。", null, actor);

        verify(repository).lockReport(reportId);
        assertThat(result)
                .containsEntry("status", "RESOLVED")
                .containsEntry("reinspectionDecision", "REQUIRED");
    }

    @Test
    void waivedDecisionRequiresHumanReason() {
        UUID reportId = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "OTHER", "NORMAL", "公共区域标识脱胶需要重新粘贴", "一层入口")));
        when(assetService.list("RESIDENT_REPORT", reportId)).thenReturn(rectificationEvidence());

        assertThatThrownBy(() -> service.submitRectification(
                reportId,
                "已重新固定标识并完成清理。",
                null,
                "WAIVED",
                "",
                UUID.randomUUID()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("人工判断理由");
    }

    @Test
    void overridingRecommendationRequiresHumanReason() {
        UUID reportId = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "WALL_CRACK", "HIGH", "外墙裂缝已完成封闭", "三层外墙")));
        when(assetService.list("RESIDENT_REPORT", reportId)).thenReturn(rectificationEvidence());

        assertThatThrownBy(() -> service.submitRectification(
                reportId,
                "已完成裂缝封闭及防水处理。",
                null,
                "WAIVED",
                null,
                UUID.randomUUID()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("人工判断理由");
    }

    @Test
    void waivedDecisionWithEvidenceAndReasonClosesDirectlyAndRecordsOverride() {
        UUID reportId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "WALL_CRACK", "HIGH", "外墙裂缝已完成封闭", "三层外墙")));
        when(assetService.list("RESIDENT_REPORT", reportId)).thenReturn(rectificationEvidence());
        when(feedbackService.updateStatus(eq(reportId), anyMap(), eq(actor)))
                .thenReturn(Map.of("reportId", reportId, "status", "CLOSED"));

        Map<String, Object> result = service.submitRectification(
                reportId,
                "已完成裂缝封闭及防水处理。",
                null,
                "WAIVED",
                "现场已由负责人核对整改前后照片，问题范围明确且无需再次派员。",
                actor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> statusBody = ArgumentCaptor.forClass(Map.class);
        verify(repository).lockReport(reportId);
        verify(feedbackService, times(2)).updateStatus(eq(reportId), statusBody.capture(), eq(actor));
        assertThat(statusBody.getAllValues()).hasSize(2);
        assertThat(statusBody.getAllValues().get(0)).containsEntry("status", "RESOLVED");
        assertThat(statusBody.getAllValues().get(1)).containsEntry("status", "CLOSED");
        assertThat(result)
                .containsEntry("status", "CLOSED")
                .containsEntry("reinspectionDecision", "WAIVED")
                .containsEntry("recommendedDecision", "REQUIRED")
                .containsEntry("manualOverride", true)
                .containsEntry("formalRiskChanged", false);
    }

    @Test
    void activeReinspectionTaskBlocksAnyNewRectificationSubmission() {
        UUID reportId = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "PROCESSING", "OTHER", "NORMAL", "再次整改已经完成", "一层入口")));
        when(assetService.list("RESIDENT_REPORT", reportId)).thenReturn(rectificationEvidence());
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.of(Map.of(
                "taskId", UUID.randomUUID(),
                "taskCode", "IT-RECHECK-ACTIVE",
                "status", "IN_PROGRESS",
                "resultRecorded", false)));

        for (String decision : List.of("REQUIRED", "WAIVED")) {
            assertThatThrownBy(() -> service.submitRectification(
                    reportId,
                    "已完成新一轮整改并准备重新提交。",
                    null,
                    decision,
                    "当前人工决策已有充分依据。",
                    UUID.randomUUID()))
                    .isInstanceOf(ResourceConflictException.class)
                    .hasMessageContaining("有效复查任务");
        }
        verify(feedbackService, never()).updateStatus(eq(reportId), anyMap(), any());
    }

    @Test
    void resolvedReportWithoutActiveTaskCanBeWaivedByHuman() {
        UUID reportId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "RESOLVED", "OTHER", "NORMAL", "整改已经完成", "一层入口")));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.empty());
        when(feedbackService.updateStatus(eq(reportId), anyMap(), eq(actor)))
                .thenReturn(Map.of("reportId", reportId, "status", "CLOSED"));

        Map<String, Object> result = service.waiveReinspection(
                reportId,
                "复核整改资料后确认无需再次到场。",
                actor);

        verify(repository).lockReport(reportId);
        assertThat(result)
                .containsEntry("status", "CLOSED")
                .containsEntry("reinspectionDecision", "WAIVED");
    }

    @Test
    void activeReinspectionTaskCannotBeBypassedByWaiver() {
        UUID reportId = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(report(
                reportId, "RESOLVED", "OTHER", "NORMAL", "整改已经完成", "一层入口")));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.of(Map.of(
                "taskId", UUID.randomUUID(),
                "taskCode", "IT-RECHECK-001",
                "status", "IN_PROGRESS",
                "resultRecorded", false)));

        assertThatThrownBy(() -> service.waiveReinspection(
                reportId,
                "希望直接关闭。",
                UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("复查任务");
        verify(repository).lockReport(reportId);
        verify(feedbackService, never()).updateStatus(eq(reportId), anyMap(), any());
    }

    private static Map<String, Object> report(
            UUID reportId,
            String status,
            String reportType,
            String urgency,
            String description,
            String location) {
        return Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-DECISION",
                "status", status,
                "reportType", reportType,
                "urgency", urgency,
                "description", description,
                "locationText", location,
                "buildingId", UUID.randomUUID());
    }

    private static List<Map<String, Object>> rectificationEvidence() {
        return List.of(Map.of(
                "assetId", UUID.randomUUID(),
                "bindingRole", "RECTIFICATION_PHOTO"));
    }
}
