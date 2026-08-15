package org.urbansafe.priority.feedback.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

/**
 * 公众反馈整改闭环编排服务。
 *
 * <p>反馈进入 RESOLVED 后创建复查复验巡检任务；巡检任务完成后，
 * 根据复验结果将反馈关闭或退回继续处理。反馈与巡检任务的关联记录在
 * resident_report_event.event_data 中，避免额外维护一套关联状态。</p>
 */
@Service
public class FeedbackClosureService {

    private final FeedbackRepository repository;
    private final FeedbackClosureRepository closureRepository;
    private final Phase2InspectionService inspectionService;
    private final FeedbackService feedbackService;

    public FeedbackClosureService(
            FeedbackRepository repository,
            FeedbackClosureRepository closureRepository,
            Phase2InspectionService inspectionService,
            FeedbackService feedbackService) {
        this.repository = repository;
        this.closureRepository = closureRepository;
        this.inspectionService = inspectionService;
        this.feedbackService = feedbackService;
    }

    /**
     * 为已完成整改的反馈创建复查复验任务。
     * 同一反馈已经存在复查任务时直接复用，避免重复派单。
     */
    @Transactional
    public Map<String, Object> createReinspection(UUID reportId, UUID actor) {
        Map<String, Object> report = requireResolvedReport(reportId);

        var existing = closureRepository.latestReinspection(reportId);
        if (existing.isPresent()) {
            Map<String, Object> reused = new LinkedHashMap<>(existing.get());
            reused.put("reused", true);
            return reused;
        }

        Object buildingValue = report.get("buildingId");
        if (buildingValue == null) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_BUILDING_REQUIRED",
                    "反馈未关联楼栋，暂不能创建复查任务");
        }
        UUID buildingId = toUuid(buildingValue, "反馈关联楼栋无效，暂不能创建复查任务");

        Map<String, Object> taskBody = new LinkedHashMap<>();
        taskBody.put("buildingId", buildingId.toString());
        taskBody.put("inspectionType", "REINSPECTION");
        taskBody.put("title", "公众反馈整改复查复验");
        taskBody.put("description", "反馈 " + String.valueOf(report.get("reportCode")) + " 整改完成后的复查复验任务");

        Map<String, Object> task = inspectionService.createTask(taskBody);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", task.get("taskId"));
        eventData.put("taskCode", task.get("taskCode"));
        eventData.put("buildingId", buildingId);
        repository.insertEvent(
                reportId,
                "REINSPECTION_CREATED",
                "RESOLVED",
                "RESOLVED",
                "整改已完成，已安排复查复验。",
                "PUBLIC",
                "STAFF",
                actor,
                eventData);

        Map<String, Object> result = new LinkedHashMap<>(task);
        result.put("reused", false);
        return result;
    }

    /**
     * 提交复查复验结论。通过则关闭反馈；未通过则退回 PROCESSING 继续整改。
     */
    @Transactional
    public Map<String, Object> completeReinspection(
            UUID reportId,
            boolean passed,
            String note,
            UUID actor) {
        requireResolvedReport(reportId);
        Map<String, Object> task = closureRepository.latestReinspection(reportId)
                .orElseThrow(() -> new ResourceConflictException(
                        "FEEDBACK_REINSPECTION_REQUIRED",
                        "请先创建并完成复查任务后再提交复验结论"));

        if (!"COMPLETED".equals(String.valueOf(task.get("status")))) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_NOT_COMPLETED",
                    "请等待复查任务完成后再提交复验结论");
        }

        String targetStatus = passed ? "CLOSED" : "PROCESSING";
        Map<String, Object> statusBody = new LinkedHashMap<>();
        statusBody.put("status", targetStatus);
        if (note != null && !note.isBlank()) {
            statusBody.put("handlingSummary", note.trim());
        }
        statusBody.put("publicVisible", true);

        Map<String, Object> updated = feedbackService.updateStatus(reportId, statusBody, actor);

        String eventType = passed ? "REINSPECTION_PASSED" : "REINSPECTION_FAILED";
        String message = passed
                ? "复查复验通过，整改事项已闭环。"
                : "复查复验未通过，已退回继续整改。";
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", task.get("taskId"));
        eventData.put("taskCode", task.get("taskCode"));
        eventData.put("passed", passed);
        if (note != null && !note.isBlank()) {
            eventData.put("note", note.trim());
        }
        repository.insertEvent(
                reportId,
                eventType,
                "RESOLVED",
                targetStatus,
                message,
                "PUBLIC",
                "STAFF",
                actor,
                eventData);

        Map<String, Object> result = new LinkedHashMap<>(updated);
        result.put("taskId", task.get("taskId"));
        result.put("taskCode", task.get("taskCode"));
        result.put("reinspectionPassed", passed);
        return result;
    }

    private Map<String, Object> requireResolvedReport(UUID reportId) {
        Map<String, Object> report = repository.findReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_REPORT_NOT_FOUND",
                        "反馈工单不存在"));
        if (!"RESOLVED".equals(String.valueOf(report.get("status")))) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_STATUS_CONFLICT",
                    "只有已完成整改的反馈才能进入复查复验流程");
        }
        return report;
    }

    private UUID toUuid(Object value, String message) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new ResourceConflictException("FEEDBACK_REINSPECTION_BUILDING_INVALID", message);
        }
    }
}
