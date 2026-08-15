package org.urbansafe.priority.feedback.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

@Service
public class FeedbackClosureService {
    private static final String FEEDBACK_BUSINESS_TYPE = "RESIDENT_REPORT";
    private static final String RECTIFICATION_PHOTO = "RECTIFICATION_PHOTO";
    private final FeedbackRepository repository;
    private final FeedbackClosureRepository closureRepository;
    private final Phase2InspectionService inspectionService;
    private final Phase2AssetService assetService;
    private final FeedbackService feedbackService;

    public FeedbackClosureService(FeedbackRepository repository,
            FeedbackClosureRepository closureRepository,
            Phase2InspectionService inspectionService,
            Phase2AssetService assetService,
            FeedbackService feedbackService) {
        this.repository = repository;
        this.closureRepository = closureRepository;
        this.inspectionService = inspectionService;
        this.assetService = assetService;
        this.feedbackService = feedbackService;
    }

    @Transactional
    public Map<String, Object> submitRectification(UUID reportId, String handlingSummary,
            String publicMessage, UUID actor) {
        Map<String, Object> report = requireStatus(reportId, "PROCESSING", "只有处理中工单可以提交整改完成");
        String normalizedSummary = normalizeRequiredText(handlingSummary,
                "FEEDBACK_RECTIFICATION_SUMMARY_INVALID", "整改说明", 4, 2000);
        String normalizedMessage = publicMessage == null || publicMessage.isBlank()
                ? "整改已完成，等待复查复验。"
                : normalizeRequiredText(publicMessage,
                        "FEEDBACK_RECTIFICATION_MESSAGE_INVALID", "公开进度说明", 2, 1000);
        List<Map<String, Object>> assets = assetService.list(FEEDBACK_BUSINESS_TYPE, reportId);
        long evidenceCount = assets.stream()
                .filter(item -> RECTIFICATION_PHOTO.equals(String.valueOf(item.get("bindingRole"))))
                .count();
        if (evidenceCount <= 0) {
            throw new ResourceConflictException("FEEDBACK_RECTIFICATION_EVIDENCE_REQUIRED",
                    "提交整改完成前至少上传一张整改证据图片");
        }
        Map<String, Object> statusBody = new LinkedHashMap<>();
        statusBody.put("status", "RESOLVED");
        statusBody.put("handlingSummary", normalizedSummary);
        statusBody.put("message", normalizedMessage);
        statusBody.put("publicVisible", true);
        feedbackService.updateStatus(reportId, statusBody, actor);
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("rectificationEvidenceCount", evidenceCount);
        eventData.put("requiresReinspection", true);
        eventData.put("formalRiskChanged", false);
        repository.insertEvent(reportId, "RECTIFICATION_SUBMITTED", "PROCESSING", "RESOLVED",
                "整改材料已提交，等待复查复验。", "PUBLIC", "STAFF", actor, eventData);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("status", "RESOLVED");
        result.put("rectificationEvidenceCount", evidenceCount);
        result.put("formalRiskChanged", false);
        result.put("nextStep", "发起复查复验任务；复验不会直接修改正式风险评分。");
        return result;
    }

    @Transactional
    public Map<String, Object> createReinspection(UUID reportId, UUID actor) {
        Map<String, Object> report = requireResolvedReport(reportId);
        var existing = closureRepository.latestReinspection(reportId);
        if (existing.isPresent() && reusableReinspection(existing.get())) {
            Map<String, Object> reused = new LinkedHashMap<>(existing.get());
            reused.put("reportId", reportId);
            reused.put("reportCode", report.get("reportCode"));
            reused.put("reused", true);
            reused.put("formalRiskChanged", false);
            return reused;
        }
        Object buildingValue = report.get("buildingId");
        if (buildingValue == null) {
            throw new ResourceConflictException("FEEDBACK_REINSPECTION_BUILDING_REQUIRED",
                    "反馈未关联楼栋，暂不能创建复查任务");
        }
        UUID buildingId = toUuid(buildingValue, "反馈关联楼栋无效，暂不能创建复查任务");
        Map<String, Object> taskBody = new LinkedHashMap<>();
        taskBody.put("buildingId", buildingId.toString());
        taskBody.put("inspectionType", "REINSPECTION");
        taskBody.put("title", "整改复查复验 · " + String.valueOf(report.get("reportCode")));
        taskBody.put("description", "复查已完成整改的问题位置，核对整改证据并记录是否通过复验。");
        Map<String, Object> task = inspectionService.createTask(taskBody);
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", task.get("taskId"));
        eventData.put("taskCode", task.get("taskCode"));
        eventData.put("buildingId", buildingId);
        eventData.put("inspectionType", "REINSPECTION");
        eventData.put("formalRiskChanged", false);
        repository.insertEvent(reportId, "REINSPECTION_CREATED", "RESOLVED", "RESOLVED",
                "整改已完成，已安排复查复验。", "PUBLIC", "STAFF", actor, eventData);
        Map<String, Object> result = new LinkedHashMap<>(task);
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("reused", false);
        result.put("formalRiskChanged", false);
        result.put("formalRiskNotice", "复验不会直接修改正式风险评分；如形成新证据，应重新执行正式评分。");
        return result;
    }

    @Transactional
    public Map<String, Object> completeReinspection(UUID reportId, boolean passed, String note, UUID actor) {
        Map<String, Object> report = requireResolvedReport(reportId);
        String normalizedNote = normalizeRequiredText(note,
                "FEEDBACK_REINSPECTION_SUMMARY_INVALID", "复验说明", 4, 2000);
        Map<String, Object> task = closureRepository.latestReinspection(reportId)
                .orElseThrow(() -> new ResourceConflictException("FEEDBACK_REINSPECTION_REQUIRED",
                        "请先创建并完成复查任务后再提交复验结论"));
        if (Boolean.TRUE.equals(task.get("resultRecorded"))) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_RESULT_ALREADY_RECORDED",
                    "该复查任务已提交复验结论，请发起新一轮复查任务");
        }
        if (!"COMPLETED".equals(String.valueOf(task.get("status")))) {
            throw new ResourceConflictException("FEEDBACK_REINSPECTION_NOT_COMPLETED",
                    "请等待复查任务完成后再提交复验结论");
        }
        String targetStatus = passed ? "CLOSED" : "PROCESSING";
        String publicMessage = passed ? "复查复验通过，整改事项已闭环。" : "复查复验未通过，已退回继续整改。";
        Map<String, Object> statusBody = new LinkedHashMap<>();
        statusBody.put("status", targetStatus);
        statusBody.put("handlingSummary", normalizedNote);
        statusBody.put("message", publicMessage);
        statusBody.put("publicVisible", true);
        Map<String, Object> updated = feedbackService.updateStatus(reportId, statusBody, actor);
        String eventType = passed ? "REINSPECTION_PASSED" : "REINSPECTION_FAILED";
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", task.get("taskId"));
        eventData.put("taskCode", task.get("taskCode"));
        eventData.put("passed", passed);
        eventData.put("summary", normalizedNote);
        eventData.put("formalRiskChanged", false);
        repository.insertEvent(reportId, eventType, "RESOLVED", targetStatus, publicMessage,
                "PUBLIC", "STAFF", actor, eventData);
        Map<String, Object> result = new LinkedHashMap<>(updated);
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("taskId", task.get("taskId"));
        result.put("taskCode", task.get("taskCode"));
        result.put("reinspectionPassed", passed);
        result.put("formalRiskChanged", false);
        result.put("nextStep", passed
                ? "治理工单已闭环；如本次复验形成新证据，请重新执行正式风险评分。"
                : "继续整改并补充新的整改证据后，再次提交整改完成并发起复查复验。");
        return result;
    }

    public Map<String, Object> latestReinspection(UUID reportId) {
        repository.findReport(reportId).orElseThrow(() -> new ResourceNotFoundException(
                "FEEDBACK_REPORT_NOT_FOUND", "反馈工单不存在"));
        return closureRepository.latestReinspection(reportId).orElse(null);
    }

    private static boolean reusableReinspection(Map<String, Object> task) {
        String status = String.valueOf(task.get("status"));
        boolean resultRecorded = Boolean.TRUE.equals(task.get("resultRecorded"));
        return !"CANCELLED".equals(status)
                && !("COMPLETED".equals(status) && resultRecorded);
    }

    private Map<String, Object> requireResolvedReport(UUID reportId) {
        return requireStatus(reportId, "RESOLVED", "只有已完成整改、等待复验的反馈才能进入复查复验流程");
    }
    private Map<String, Object> requireStatus(UUID reportId, String expected, String message) {
        Map<String, Object> report = repository.findReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("FEEDBACK_REPORT_NOT_FOUND", "反馈工单不存在"));
        if (!expected.equals(String.valueOf(report.get("status")))) {
            throw new ResourceConflictException("FEEDBACK_CLOSURE_STATUS_CONFLICT", message);
        }
        return report;
    }
    private static String normalizeRequiredText(String text, String errorCode, String label, int min, int max) {
        String value = text == null ? "" : text.trim();
        if (value.length() < min || value.length() > max) {
            throw new InvalidRequestException(errorCode, label + "长度必须在 " + min + " 至 " + max + " 个字符之间");
        }
        return value;
    }
    private UUID toUuid(Object value, String message) {
        if (value instanceof UUID uuid) return uuid;
        try { return UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ex) {
            throw new ResourceConflictException("FEEDBACK_REINSPECTION_BUILDING_INVALID", message);
        }
    }
}
