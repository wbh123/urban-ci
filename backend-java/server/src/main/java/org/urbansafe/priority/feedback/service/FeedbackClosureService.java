package org.urbansafe.priority.feedback.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

/**
 * 公众反馈整改闭环编排服务。
 *
 * <p>整改完成必须有整改证据。系统根据结构化事实给出复检建议，最终由人工选择
 * REQUIRED（需要现场复检）或 WAIVED（无需现场复检）。人工可覆盖建议，但必须留痕；
 * 已创建的有效复检任务不能通过免复检操作绕过。任何治理闭环操作均不直接修改正式风险评分。</p>
 */
@Service
public class FeedbackClosureService {

    public static final String REINSPECTION_REQUIRED = "REQUIRED";
    public static final String REINSPECTION_WAIVED = "WAIVED";
    private static final String FEEDBACK_BUSINESS_TYPE = "RESIDENT_REPORT";
    private static final String RECTIFICATION_PHOTO = "RECTIFICATION_PHOTO";
    private static final String RECOMMENDATION_SOURCE = "STRUCTURED_RULES";
    private static final String RECOMMENDATION_DISCLAIMER =
            "系统复检建议仅用于辅助决策，不替代现场人员和管理人员判断；最终决策及人工覆盖理由将留痕。";

    private final FeedbackRepository repository;
    private final FeedbackClosureRepository closureRepository;
    private final Phase2InspectionService inspectionService;
    private final Phase2AssetService assetService;
    private final FeedbackService feedbackService;

    public FeedbackClosureService(
            FeedbackRepository repository,
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

    /** 返回稳定、可解释且不依赖大模型在线状态的复检建议。 */
    public Map<String, Object> recommendReinspection(UUID reportId) {
        Map<String, Object> report = repository.findReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_REPORT_NOT_FOUND", "反馈工单不存在"));
        Recommendation recommendation = recommendation(report);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("recommendedDecision", recommendation.decision());
        result.put("reasons", recommendation.reasons());
        result.put("source", RECOMMENDATION_SOURCE);
        result.put("disclaimer", RECOMMENDATION_DISCLAIMER);
        result.put("formalRiskChanged", false);
        return result;
    }

    /**
     * 兼容旧客户端的整改提交入口。未显式传复检决策时继续默认 REQUIRED，保持原有业务行为。
     */
    @Transactional
    public Map<String, Object> submitRectification(
            UUID reportId,
            String handlingSummary,
            String publicMessage,
            UUID actor) {
        return submitRectificationInternal(
                reportId,
                handlingSummary,
                publicMessage,
                REINSPECTION_REQUIRED,
                null,
                actor,
                false);
    }

    /** 提交整改完成材料，并由人工确认是否需要现场复检。 */
    @Transactional
    public Map<String, Object> submitRectification(
            UUID reportId,
            String handlingSummary,
            String publicMessage,
            String reinspectionDecision,
            String decisionReason,
            UUID actor) {
        return submitRectificationInternal(
                reportId,
                handlingSummary,
                publicMessage,
                reinspectionDecision,
                decisionReason,
                actor,
                true);
    }

    private Map<String, Object> submitRectificationInternal(
            UUID reportId,
            String handlingSummary,
            String publicMessage,
            String reinspectionDecision,
            String decisionReason,
            UUID actor,
            boolean explicitDecision) {
        Map<String, Object> report = requireLockedStatus(
                reportId,
                "PROCESSING",
                "只有处理中工单可以提交整改完成");
        String normalizedSummary = normalizeRequiredText(
                handlingSummary,
                "FEEDBACK_RECTIFICATION_SUMMARY_INVALID",
                "整改说明",
                4,
                2000);
        long evidenceCount = rectificationEvidenceCount(reportId);
        ensureNoActiveReinspection(reportId);

        Recommendation recommendation = recommendation(report);
        String decision = normalizeDecision(reinspectionDecision);
        boolean manualOverride = explicitDecision && !decision.equals(recommendation.decision());
        String normalizedReason = normalizeOptionalText(decisionReason, 1000);
        if (REINSPECTION_WAIVED.equals(decision) || manualOverride) {
            normalizedReason = normalizeRequiredText(
                    decisionReason,
                    "FEEDBACK_REINSPECTION_DECISION_REASON_REQUIRED",
                    "人工判断理由",
                    4,
                    1000);
        }

        String targetStatus = REINSPECTION_REQUIRED.equals(decision) ? "RESOLVED" : "CLOSED";
        String defaultPublicMessage = REINSPECTION_REQUIRED.equals(decision)
                ? "整改已完成，等待复查复验。"
                : "整改已完成，经人工确认本次无需现场复检，事项已闭环。";
        String normalizedMessage = publicMessage == null || publicMessage.isBlank()
                ? defaultPublicMessage
                : normalizeRequiredText(
                        publicMessage,
                        "FEEDBACK_RECTIFICATION_MESSAGE_INVALID",
                        "公开进度说明",
                        2,
                        1000);

        if (REINSPECTION_REQUIRED.equals(decision)) {
            updateStatus(reportId, "RESOLVED", normalizedSummary, normalizedMessage, true, actor);
        } else {
            // 保持 FeedbackService 原有状态机和普通 /status 安全边界：
            // 在同一事务内先进入 RESOLVED，再由闭环专用服务进入 CLOSED。
            updateStatus(
                    reportId,
                    "RESOLVED",
                    normalizedSummary,
                    "整改材料已提交，正在执行人工免复检闭环确认。",
                    false,
                    actor);
            updateStatus(reportId, "CLOSED", normalizedSummary, normalizedMessage, true, actor);
        }

        Map<String, Object> eventData = decisionEventData(
                decision,
                recommendation,
                manualOverride,
                normalizedReason,
                evidenceCount,
                explicitDecision ? "HUMAN_CONFIRMED" : "LEGACY_DEFAULT");
        repository.insertEvent(
                reportId,
                REINSPECTION_REQUIRED.equals(decision)
                        ? "RECTIFICATION_SUBMITTED"
                        : "RECTIFICATION_CLOSED_WITHOUT_REINSPECTION",
                "PROCESSING",
                targetStatus,
                normalizedMessage,
                "PUBLIC",
                "STAFF",
                actor,
                eventData);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("status", targetStatus);
        result.put("rectificationEvidenceCount", evidenceCount);
        result.put("reinspectionDecision", decision);
        result.put("recommendedDecision", recommendation.decision());
        result.put("recommendationReasons", recommendation.reasons());
        result.put("manualOverride", manualOverride);
        result.put("decisionReason", normalizedReason);
        result.put("formalRiskChanged", false);
        result.put("nextStep", REINSPECTION_REQUIRED.equals(decision)
                ? "发起复查复验任务；复验不会直接修改正式风险评分。"
                : "人工已确认无需现场复检，治理工单已闭环；如形成新证据，应重新执行正式风险评分。");
        return result;
    }

    /**
     * 对已经进入 RESOLVED 的在途工单进行人工免复检闭环。
     * 仅在尚未存在有效复检任务时允许，避免绕过已经派出的现场任务。
     */
    @Transactional
    public Map<String, Object> waiveReinspection(UUID reportId, String decisionReason, UUID actor) {
        Map<String, Object> report = requireResolvedReport(reportId);
        String normalizedReason = normalizeRequiredText(
                decisionReason,
                "FEEDBACK_REINSPECTION_DECISION_REASON_REQUIRED",
                "人工判断理由",
                4,
                1000);
        var latestTask = closureRepository.latestReinspection(reportId);
        if (latestTask.isPresent() && activeReinspection(latestTask.get())) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_WAIVE_TASK_CONFLICT",
                    "已存在有效复查任务，不能直接免复检闭环；请先完成或按业务规则取消复查任务");
        }

        Recommendation recommendation = recommendation(report);
        boolean manualOverride = !REINSPECTION_WAIVED.equals(recommendation.decision());
        String publicMessage = "经人工复核，确认本次整改无需继续现场复检，事项已闭环。";
        Map<String, Object> updated = updateStatus(
                reportId,
                "CLOSED",
                normalizedReason,
                publicMessage,
                true,
                actor);

        Map<String, Object> eventData = decisionEventData(
                REINSPECTION_WAIVED,
                recommendation,
                manualOverride,
                normalizedReason,
                null,
                "HUMAN_CONFIRMED_AFTER_RESOLVED");
        latestTask.ifPresent(task -> {
            eventData.put("previousTaskId", task.get("taskId"));
            eventData.put("previousTaskCode", task.get("taskCode"));
            eventData.put("previousTaskStatus", task.get("status"));
        });
        repository.insertEvent(
                reportId,
                "REINSPECTION_WAIVED",
                "RESOLVED",
                "CLOSED",
                publicMessage,
                "PUBLIC",
                "STAFF",
                actor,
                eventData);

        Map<String, Object> result = new LinkedHashMap<>(updated);
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("status", "CLOSED");
        result.put("reinspectionDecision", REINSPECTION_WAIVED);
        result.put("recommendedDecision", recommendation.decision());
        result.put("recommendationReasons", recommendation.reasons());
        result.put("manualOverride", manualOverride);
        result.put("decisionReason", normalizedReason);
        result.put("formalRiskChanged", false);
        result.put("nextStep", "治理工单已闭环；如后续出现新证据，请重新发起巡检或正式风险评分。");
        return result;
    }

    /**
     * 为已完成整改的反馈创建复查复验任务。
     * 同一轮整改已有未取消且尚未提交复验结论的任务时复用；历史轮次已经形成结论时必须新建任务。
     */
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
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_BUILDING_REQUIRED",
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
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("reused", false);
        result.put("formalRiskChanged", false);
        result.put("formalRiskNotice", "复验不会直接修改正式风险评分；如形成新证据，应重新执行正式评分。");
        return result;
    }

    /** 提交复查复验结论。通过则关闭反馈；未通过则退回 PROCESSING 继续整改。 */
    @Transactional
    public Map<String, Object> completeReinspection(
            UUID reportId,
            boolean passed,
            String note,
            UUID actor) {
        Map<String, Object> report = requireResolvedReport(reportId);
        String normalizedNote = normalizeRequiredText(
                note,
                "FEEDBACK_REINSPECTION_SUMMARY_INVALID",
                "复验说明",
                4,
                2000);
        Map<String, Object> task = closureRepository.latestReinspection(reportId)
                .orElseThrow(() -> new ResourceConflictException(
                        "FEEDBACK_REINSPECTION_REQUIRED",
                        "请先创建并完成复查任务后再提交复验结论"));

        if (Boolean.TRUE.equals(task.get("resultRecorded"))) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_RESULT_ALREADY_RECORDED",
                    "该复查任务已提交复验结论，请发起新一轮复查任务");
        }
        if (!"COMPLETED".equals(String.valueOf(task.get("status")))) {
            throw new ResourceConflictException(
                    "FEEDBACK_REINSPECTION_NOT_COMPLETED",
                    "请等待复查任务完成后再提交复验结论");
        }

        String targetStatus = passed ? "CLOSED" : "PROCESSING";
        String publicMessage = passed
                ? "复查复验通过，整改事项已闭环。"
                : "复查复验未通过，已退回继续整改。";
        Map<String, Object> updated = updateStatus(
                reportId,
                targetStatus,
                normalizedNote,
                publicMessage,
                true,
                actor);

        String eventType = passed ? "REINSPECTION_PASSED" : "REINSPECTION_FAILED";
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("taskId", task.get("taskId"));
        eventData.put("taskCode", task.get("taskCode"));
        eventData.put("passed", passed);
        eventData.put("summary", normalizedNote);
        eventData.put("reinspectionDecision", REINSPECTION_REQUIRED);
        eventData.put("formalRiskChanged", false);
        repository.insertEvent(
                reportId,
                eventType,
                "RESOLVED",
                targetStatus,
                publicMessage,
                "PUBLIC",
                "STAFF",
                actor,
                eventData);

        Map<String, Object> result = new LinkedHashMap<>(updated);
        result.put("reportId", reportId);
        result.put("reportCode", report.get("reportCode"));
        result.put("taskId", task.get("taskId"));
        result.put("taskCode", task.get("taskCode"));
        result.put("reinspectionPassed", passed);
        result.put("reinspectionDecision", REINSPECTION_REQUIRED);
        result.put("formalRiskChanged", false);
        result.put("nextStep", passed
                ? "治理工单已闭环；如本次复验形成新证据，请重新执行正式风险评分。"
                : "继续整改并补充新的整改证据后，再次提交整改完成并发起复查复验。");
        return result;
    }

    public Map<String, Object> latestReinspection(UUID reportId) {
        repository.findReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_REPORT_NOT_FOUND",
                        "反馈工单不存在"));
        return closureRepository.latestReinspection(reportId).orElse(null);
    }

    private Map<String, Object> updateStatus(
            UUID reportId,
            String status,
            String summary,
            String message,
            boolean publicVisible,
            UUID actor) {
        Map<String, Object> statusBody = new LinkedHashMap<>();
        statusBody.put("status", status);
        statusBody.put("handlingSummary", summary);
        statusBody.put("message", message);
        statusBody.put("publicVisible", publicVisible);
        return feedbackService.updateStatus(reportId, statusBody, actor);
    }

    private long rectificationEvidenceCount(UUID reportId) {
        List<Map<String, Object>> assets = assetService.list(FEEDBACK_BUSINESS_TYPE, reportId);
        long evidenceCount = assets.stream()
                .filter(item -> RECTIFICATION_PHOTO.equals(String.valueOf(item.get("bindingRole"))))
                .count();
        if (evidenceCount <= 0) {
            throw new ResourceConflictException(
                    "FEEDBACK_RECTIFICATION_EVIDENCE_REQUIRED",
                    "提交整改完成前至少上传一张整改证据图片");
        }
        return evidenceCount;
    }

    private void ensureNoActiveReinspection(UUID reportId) {
        var latestTask = closureRepository.latestReinspection(reportId);
        if (latestTask.isPresent() && activeReinspection(latestTask.get())) {
            throw new ResourceConflictException(
                    "FEEDBACK_RECTIFICATION_ACTIVE_REINSPECTION_CONFLICT",
                    "当前仍存在有效复查任务，不能提交新一轮整改完成；请先完成或按业务规则取消现有复查任务");
        }
    }

    private static Map<String, Object> decisionEventData(
            String decision,
            Recommendation recommendation,
            boolean manualOverride,
            String decisionReason,
            Long evidenceCount,
            String decisionSource) {
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("reinspectionDecision", decision);
        eventData.put("recommendedDecision", recommendation.decision());
        eventData.put("recommendationReasons", recommendation.reasons());
        eventData.put("recommendationSource", RECOMMENDATION_SOURCE);
        eventData.put("decisionSource", decisionSource);
        eventData.put("manualOverride", manualOverride);
        if (decisionReason != null) eventData.put("decisionReason", decisionReason);
        if (evidenceCount != null) eventData.put("rectificationEvidenceCount", evidenceCount);
        eventData.put("requiresReinspection", REINSPECTION_REQUIRED.equals(decision));
        eventData.put("formalRiskChanged", false);
        return eventData;
    }

    private static Recommendation recommendation(Map<String, Object> report) {
        List<String> reasons = new ArrayList<>();
        String urgency = text(report.get("urgency")).toUpperCase(Locale.ROOT);
        String reportType = text(report.get("reportType")).toUpperCase(Locale.ROOT);
        String haystack = (text(report.get("description")) + " " + text(report.get("locationText")))
                .toLowerCase(Locale.ROOT);

        if ("URGENT".equals(urgency) || "HIGH".equals(urgency)) {
            reasons.add("紧急程度为 " + urgency + "，建议通过现场复检确认整改效果");
        }
        if (List.of("WALL_CRACK", "SURFACE_FALLING", "ILLEGAL_MODIFICATION", "FIRE_ACCESS")
                .contains(reportType)) {
            reasons.add("问题类型 " + reportType + " 涉及结构、坠落、违规改造或消防等现场核实事项");
        }
        List<String> riskKeywords = List.of(
                "裂缝", "开裂", "倾斜", "沉降", "露筋", "钢筋", "脱落", "剥落",
                "变形", "承重", "消防", "坠落", "松动", "锈蚀");
        List<String> matched = riskKeywords.stream().filter(haystack::contains).distinct().toList();
        if (!matched.isEmpty()) {
            reasons.add("问题描述命中需现场核实的风险信号：" + String.join("、", matched));
        }

        if (!reasons.isEmpty()) {
            return new Recommendation(REINSPECTION_REQUIRED, List.copyOf(reasons));
        }
        return new Recommendation(
                REINSPECTION_WAIVED,
                List.of("当前结构化信息未发现高紧急程度、重点问题类型或明显现场风险信号，可由人工结合整改证据判断是否免复检"));
    }

    private static boolean activeReinspection(Map<String, Object> task) {
        String status = String.valueOf(task.get("status"));
        boolean resultRecorded = Boolean.TRUE.equals(task.get("resultRecorded"));
        if ("CANCELLED".equals(status)) return false;
        return !("COMPLETED".equals(status) && resultRecorded);
    }

    private static boolean reusableReinspection(Map<String, Object> task) {
        return activeReinspection(task);
    }

    private Map<String, Object> requireResolvedReport(UUID reportId) {
        return requireLockedStatus(
                reportId,
                "RESOLVED",
                "只有已完成整改、等待复验的反馈才能进入复查复验流程");
    }

    private Map<String, Object> requireLockedStatus(UUID reportId, String expected, String message) {
        Map<String, Object> report = repository.lockReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_REPORT_NOT_FOUND",
                        "反馈工单不存在"));
        if (!expected.equals(String.valueOf(report.get("status")))) {
            throw new ResourceConflictException("FEEDBACK_CLOSURE_STATUS_CONFLICT", message);
        }
        return report;
    }

    private static String normalizeDecision(String value) {
        String decision = value == null || value.isBlank()
                ? REINSPECTION_REQUIRED
                : value.trim().toUpperCase(Locale.ROOT);
        if (!REINSPECTION_REQUIRED.equals(decision) && !REINSPECTION_WAIVED.equals(decision)) {
            throw new InvalidRequestException(
                    "FEEDBACK_REINSPECTION_DECISION_INVALID",
                    "reinspectionDecision 只能为 REQUIRED 或 WAIVED");
        }
        return decision;
    }

    private static String normalizeRequiredText(
            String text,
            String errorCode,
            String label,
            int min,
            int max) {
        String value = text == null ? "" : text.trim();
        if (value.length() < min || value.length() > max) {
            throw new InvalidRequestException(
                    errorCode,
                    label + "长度必须在 " + min + " 至 " + max + " 个字符之间");
        }
        return value;
    }

    private static String normalizeOptionalText(String text, int max) {
        if (text == null || text.isBlank()) return null;
        String value = text.trim();
        if (value.length() > max) {
            throw new InvalidRequestException(
                    "FEEDBACK_REINSPECTION_DECISION_REASON_INVALID",
                    "人工判断理由不能超过 " + max + " 个字符");
        }
        return value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private record Recommendation(String decision, List<String> reasons) {
    }
}
