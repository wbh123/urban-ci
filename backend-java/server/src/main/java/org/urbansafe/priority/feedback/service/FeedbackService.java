package org.urbansafe.priority.feedback.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;

/** 公众反馈应用服务：匿名提交、双凭证查询、图片上传、工作人员代录和状态流转。 */
@Service
public class FeedbackService {

    public static final String DISCLAIMER =
            "反馈内容将作为巡检和治理线索，不代表正式房屋安全鉴定结论。"
                    + "紧急危险情况请优先联系当地应急、消防或政务服务热线。";
    public static final int MAX_PUBLIC_IMAGES = 6;

    private static final String FEEDBACK_BUSINESS_TYPE = "RESIDENT_REPORT";
    private static final String FEEDBACK_IMAGE_ROLE = "FEEDBACK_PHOTO";
    private static final Set<String> IMAGE_UPLOAD_TERMINAL_STATUSES =
            Set.of("CLOSED", "REJECTED", "CANCELLED");
    private static final Set<String> CHANNELS = Set.of("WEB", "PHONE", "SMS", "COUNTER", "INTERNAL");
    private static final Set<String> MANUAL_CHANNELS = Set.of("PHONE", "SMS", "COUNTER", "INTERNAL");
    private static final Set<String> URGENCIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> REPORT_TYPES = Set.of(
            "WALL_CRACK", "SURFACE_FALLING", "WATER_LEAKAGE",
            "ILLEGAL_MODIFICATION", "FIRE_ACCESS", "OTHER");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("ACCEPTED", "REJECTED", "CANCELLED"),
            "ACCEPTED", Set.of("PROCESSING", "NEED_MORE_INFO", "REJECTED", "CANCELLED"),
            "PROCESSING", Set.of("NEED_MORE_INFO", "RESOLVED", "CANCELLED"),
            "NEED_MORE_INFO", Set.of("PROCESSING", "CANCELLED"),
            "RESOLVED", Set.of("CLOSED", "PROCESSING"),
            "CLOSED", Set.of(),
            "REJECTED", Set.of(),
            "CANCELLED", Set.of());

    private final FeedbackRepository repository;
    private final Phase2AssetService assetService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public FeedbackService(
            FeedbackRepository repository,
            Phase2AssetService assetService,
            AuditService auditService) {
        this.repository = repository;
        this.assetService = assetService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listPublicCommunities() {
        return repository.listPublicCommunities();
    }

    public List<Map<String, Object>> listPublicBuildings(UUID communityId) {
        if (!repository.communityExists(communityId)) {
            throw new ResourceNotFoundException("FEEDBACK_COMMUNITY_NOT_FOUND", "小区不存在或不可用");
        }
        return repository.listPublicBuildings(communityId);
    }

    @Transactional
    public Map<String, Object> createPublic(Map<String, Object> body) {
        return create(body, "WEB", null, null, "CITIZEN");
    }

    @Transactional
    public Map<String, Object> createManual(Map<String, Object> body, UUID recordedBy) {
        String channel = normalized(body.get("feedbackChannel"), "feedbackChannel");
        if (!MANUAL_CHANNELS.contains(channel)) {
            throw new InvalidRequestException(
                    "FEEDBACK_CHANNEL_INVALID", "工作人员代录仅支持 PHONE、SMS、COUNTER 或 INTERNAL");
        }
        Map<String, Object> result = create(body, channel, recordedBy, recordedBy, "STAFF");
        auditService.recordSuccess(AuditOperation.success(
                "FEEDBACK_MANUAL_CREATE", "ResidentReport", (UUID) result.get("reportId"),
                null, safeAuditSnapshot(result), List.of(), "工作人员代录公众反馈"));
        return result;
    }

    private Map<String, Object> create(Map<String, Object> body, String channel,
            UUID reporterUserId, UUID recordedBy, String actorType) {
        UUID communityId = uuid(body.get("communityId"), "communityId", true);
        UUID buildingId = uuid(body.get("buildingId"), "buildingId", false);
        if (!repository.communityExists(communityId)) {
            throw new ResourceNotFoundException("FEEDBACK_COMMUNITY_NOT_FOUND", "小区不存在或不可用");
        }
        if (buildingId != null && !repository.buildingBelongsToCommunity(buildingId, communityId)) {
            throw new InvalidRequestException("FEEDBACK_BUILDING_MISMATCH", "楼栋不属于所选小区");
        }

        String reportType = upper(body.get("reportType"), "OTHER");
        if (!REPORT_TYPES.contains(reportType)) {
            throw new InvalidRequestException("FEEDBACK_TYPE_INVALID", "反馈类型无效");
        }
        String urgency = upper(body.get("urgency"), "NORMAL");
        if (!URGENCIES.contains(urgency)) {
            throw new InvalidRequestException("FEEDBACK_URGENCY_INVALID", "紧急程度无效");
        }
        if (!CHANNELS.contains(channel)) {
            throw new InvalidRequestException("FEEDBACK_CHANNEL_INVALID", "反馈渠道无效");
        }

        String description = requiredText(body.get("description"), "description", 10, 2000);
        String reporterName = optionalText(body.get("reporterName"), 128);
        String contactPhone = optionalText(body.get("contactPhone"), 32);
        String contactEmail = optionalText(body.get("contactEmail"), 255);
        String locationText = optionalText(body.get("locationText"), 512);
        boolean contactConsent = booleanValue(body.get("contactConsent"));
        if ((contactPhone != null || contactEmail != null) && !contactConsent) {
            throw new InvalidRequestException(
                    "FEEDBACK_CONTACT_CONSENT_REQUIRED", "填写联系方式时需要确认同意用于反馈处理联系");
        }

        UUID reportId = UUID.randomUUID();
        String reportCode = generateReportCode();
        String trackingSecret = generateTrackingSecret();
        String trackingHash = hashTrackingSecret(trackingSecret);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", reportId);
        report.put("reportCode", reportCode);
        report.put("communityId", communityId);
        report.put("buildingId", buildingId);
        report.put("reporterUserId", reporterUserId);
        report.put("reporterName", reporterName);
        report.put("contactPhone", contactPhone);
        report.put("contactEmail", contactEmail);
        report.put("contactConsent", contactConsent);
        report.put("feedbackChannel", channel);
        report.put("locationText", locationText);
        report.put("recordedBy", recordedBy);
        report.put("reportType", reportType);
        report.put("description", description);
        report.put("urgency", urgency);
        report.put("evidence", "[]");
        report.put("trackingSecretHash", trackingHash);
        report.put("updatedBy", recordedBy);
        repository.insertReport(report);
        repository.insertEvent(
                reportId, "CREATED", null, "SUBMITTED",
                "反馈已提交，等待工作人员受理。", "PUBLIC", actorType, recordedBy,
                Map.of("feedbackChannel", channel));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("reportCode", reportCode);
        result.put("trackingSecret", trackingSecret);
        result.put("status", "SUBMITTED");
        result.put("feedbackChannel", channel);
        result.put("submittedAt", OffsetDateTime.now().toString());
        result.put("maxImageCount", MAX_PUBLIC_IMAGES);
        result.put("disclaimer", DISCLAIMER);
        return result;
    }

    /**
     * 创建反馈后的第二步图片上传。反馈行锁保证并发请求不能同时绕过 6 张上限。
     */
    @Transactional
    public Map<String, Object> uploadPublicImage(
            String reportCode, String trackingSecret, MultipartFile file) {
        Map<String, Object> report = authorizePublicReport(reportCode, trackingSecret, true);
        UUID reportId = (UUID) report.get("reportId");
        String status = String.valueOf(report.get("status"));
        if (IMAGE_UPLOAD_TERMINAL_STATUSES.contains(status)) {
            throw new ResourceConflictException(
                    "FEEDBACK_IMAGE_UPLOAD_CLOSED", "当前反馈状态不允许继续上传图片");
        }

        int existingCount = repository.countReportImages(reportId);
        if (existingCount >= MAX_PUBLIC_IMAGES) {
            throw new ResourceConflictException(
                    "FEEDBACK_IMAGE_LIMIT_REACHED", "每个反馈最多上传 " + MAX_PUBLIC_IMAGES + " 张图片");
        }

        Map<String, Object> saved = assetService.upload(
                file, FEEDBACK_BUSINESS_TYPE, reportId, FEEDBACK_IMAGE_ROLE);
        Map<String, Object> result = new LinkedHashMap<>(saved);
        result.remove("previewUrl");
        result.put("imageCount", existingCount + 1);
        result.put("remainingSlots", MAX_PUBLIC_IMAGES - existingCount - 1);

        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("assetId", saved.get("assetId"));
        eventData.put("originalFilename", saved.get("originalFilename"));
        repository.insertEvent(
                reportId, "IMAGE_UPLOADED", null, status,
                "反馈人补充了现场图片。", "INTERNAL", "CITIZEN", null, eventData);
        return result;
    }

    public List<Map<String, Object>> listPublicImages(String reportCode, String trackingSecret) {
        Map<String, Object> report = authorizePublicReport(reportCode, trackingSecret, false);
        return repository.listReportImages((UUID) report.get("reportId"));
    }

    public PublicImageContent publicImageContent(
            String reportCode, String trackingSecret, UUID assetId) {
        Map<String, Object> report = authorizePublicReport(reportCode, trackingSecret, false);
        UUID reportId = (UUID) report.get("reportId");
        if (!repository.assetBelongsToReport(reportId, assetId)) {
            throw new ResourceNotFoundException(
                    "FEEDBACK_IMAGE_NOT_FOUND", "图片不存在或不属于当前反馈");
        }
        Map<String, Object> asset = assetService.get(assetId);
        return new PublicImageContent(
                String.valueOf(asset.get("contentType")),
                assetService.content(assetId));
    }

    public List<Map<String, Object>> listManagementImages(UUID reportId) {
        repository.findReport(reportId).orElseThrow(() -> new ResourceNotFoundException(
                "FEEDBACK_REPORT_NOT_FOUND", "反馈工单不存在"));
        return repository.listReportImages(reportId);
    }

    public Map<String, Object> track(String reportCode, String trackingSecret) {
        Map<String, Object> report = authorizePublicReport(reportCode, trackingSecret, false);
        UUID reportId = (UUID) report.get("reportId");
        report.put("contactPhone", maskPhone((String) report.get("contactPhone")));
        report.put("contactEmail", maskEmail((String) report.get("contactEmail")));
        report.put("events", repository.listPublicEvents(reportId));
        List<Map<String, Object>> images = repository.listReportImages(reportId);
        report.put("images", images);
        report.put("imageCount", images.size());
        report.put("maxImageCount", MAX_PUBLIC_IMAGES);
        report.put("disclaimer", DISCLAIMER);
        return report;
    }

    public Map<String, Object> list(String status, String channel, UUID communityId, int page, int size) {
        String normalizedStatus = nullableUpper(status);
        String normalizedChannel = nullableUpper(channel);
        if (normalizedStatus != null && !TRANSITIONS.containsKey(normalizedStatus)) {
            throw new InvalidRequestException("FEEDBACK_STATUS_INVALID", "反馈状态无效");
        }
        if (normalizedChannel != null && !CHANNELS.contains(normalizedChannel)) {
            throw new InvalidRequestException("FEEDBACK_CHANNEL_INVALID", "反馈渠道无效");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("PAGINATION_INVALID", "分页参数范围为 page>=0 且 1<=size<=100");
        }
        List<Map<String, Object>> content = repository.listReports(
                normalizedStatus, normalizedChannel, communityId, page, size);
        long total = repository.countReports(normalizedStatus, normalizedChannel, communityId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("page", page);
        metadata.put("size", size);
        metadata.put("totalElements", total);
        metadata.put("totalPages", (int) Math.ceil((double) total / size));
        return Map.of("content", content, "page", metadata);
    }

    @Transactional
    public Map<String, Object> updateStatus(UUID reportId, Map<String, Object> body, UUID updatedBy) {
        Map<String, Object> existing = repository.lockReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_REPORT_NOT_FOUND", "反馈工单不存在"));
        String fromStatus = String.valueOf(existing.get("status"));
        String toStatus = normalized(body.get("status"), "status");
        if (!TRANSITIONS.containsKey(toStatus)) {
            throw new InvalidRequestException("FEEDBACK_STATUS_INVALID", "反馈状态无效");
        }
        if (!TRANSITIONS.getOrDefault(fromStatus, Set.of()).contains(toStatus)) {
            throw new ResourceConflictException(
                    "FEEDBACK_STATUS_CONFLICT", "不能从 " + fromStatus + " 变更为 " + toStatus);
        }
        String summary = optionalText(body.get("handlingSummary"), 2000);
        String message = optionalText(body.get("message"), 2000);
        boolean publicVisible = body.get("publicVisible") == null || booleanValue(body.get("publicVisible"));
        if (repository.updateStatus(reportId, toStatus, summary, updatedBy) == 0) {
            throw new ResourceConflictException("FEEDBACK_UPDATE_CONFLICT", "反馈工单状态已变化，请刷新后重试");
        }
        repository.insertEvent(
                reportId, "STATUS_CHANGED", fromStatus, toStatus,
                message == null ? defaultStatusMessage(toStatus) : message,
                publicVisible ? "PUBLIC" : "INTERNAL", "STAFF", updatedBy,
                Map.of("handlingSummaryUpdated", summary != null));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("reportCode", existing.get("reportCode"));
        result.put("fromStatus", fromStatus);
        result.put("status", toStatus);
        result.put("handlingSummary", summary);
        result.put("updatedAt", OffsetDateTime.now().toString());
        auditService.recordSuccess(AuditOperation.success(
                "FEEDBACK_STATUS_UPDATE", "ResidentReport", reportId,
                Map.of("status", fromStatus), safeAuditSnapshot(result),
                List.of("status", "handlingSummary"), "更新公众反馈处理状态"));
        return result;
    }

    private Map<String, Object> authorizePublicReport(
            String reportCode, String trackingSecret, boolean lock) {
        String code = requiredText(reportCode, "reportCode", 4, 64).toUpperCase();
        String secret = requiredText(trackingSecret, "trackingSecret", 8, 128);
        String trackingHash = hashTrackingSecret(secret);
        return (lock
                ? repository.lockPublicReport(code, trackingHash)
                : repository.findPublicReport(code, trackingHash))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_TRACKING_NOT_FOUND", "工单编号或查询凭证不正确"));
    }

    private Map<String, Object> safeAuditSnapshot(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("reportId", "reportCode", "status", "fromStatus", "feedbackChannel", "updatedAt")) {
            if (source.containsKey(key)) {
                result.put(key, source.get(key));
            }
        }
        return result;
    }

    private String generateReportCode() {
        return "FB-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateTrackingSecret() {
        byte[] bytes = new byte[12];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashTrackingSecret(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境不支持 SHA-256", ex);
        }
    }

    private String defaultStatusMessage(String status) {
        return switch (status) {
            case "ACCEPTED" -> "反馈已受理。";
            case "PROCESSING" -> "反馈正在处理中。";
            case "NEED_MORE_INFO" -> "处理人员需要补充信息。";
            case "RESOLVED" -> "反馈问题已处理，等待确认归档。";
            case "CLOSED" -> "反馈工单已关闭。";
            case "REJECTED" -> "反馈未被受理，请查看处理说明。";
            case "CANCELLED" -> "反馈工单已取消。";
            default -> "反馈状态已更新。";
        };
    }

    private String maskPhone(String value) {
        if (value == null || value.length() < 7) {
            return value;
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskEmail(String value) {
        if (value == null || !value.contains("@")) {
            return value;
        }
        int at = value.indexOf('@');
        String local = value.substring(0, at);
        return (local.isEmpty() ? "*" : local.substring(0, 1) + "***") + value.substring(at);
    }

    private UUID uuid(Object value, String field, boolean required) {
        if (value == null || String.valueOf(value).isBlank()) {
            if (required) {
                throw new InvalidRequestException("FEEDBACK_FIELD_REQUIRED", field + " 不能为空");
            }
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("FEEDBACK_UUID_INVALID", field + " 不是合法标识");
        }
    }

    private String normalized(Object value, String field) {
        String result = nullableUpper(value == null ? null : String.valueOf(value));
        if (result == null) {
            throw new InvalidRequestException("FEEDBACK_FIELD_REQUIRED", field + " 不能为空");
        }
        return result;
    }

    private String upper(Object value, String defaultValue) {
        String result = nullableUpper(value == null ? null : String.valueOf(value));
        return result == null ? defaultValue : result;
    }

    private String nullableUpper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String requiredText(Object value, String field, int min, int max) {
        String result = optionalText(value, max);
        if (result == null || result.length() < min) {
            throw new InvalidRequestException(
                    "FEEDBACK_FIELD_INVALID", field + " 长度不能少于 " + min + " 个字符");
        }
        return result;
    }

    private String optionalText(Object value, int max) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String result = String.valueOf(value).trim();
        if (result.length() > max) {
            throw new InvalidRequestException("FEEDBACK_FIELD_TOO_LONG", "字段长度不能超过 " + max + " 个字符");
        }
        return result;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean booleanValue
                ? booleanValue
                : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public record PublicImageContent(String contentType, byte[] bytes) {
    }
}
