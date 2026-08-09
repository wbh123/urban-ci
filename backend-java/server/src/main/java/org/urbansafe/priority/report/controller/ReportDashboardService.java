package org.urbansafe.priority.report.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.assessment.checksum.AssessmentChecksumService;
import org.urbansafe.priority.assessment.repository.AssessmentResultRepository;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

@Service
public class ReportDashboardService {

    static final String TEMPLATE_VERSION = "phase5-report-v1";
    private static final String DISCLAIMER = AssessmentResultRepository.disclaimer();

    private final ReportDashboardRepository repository;
    private final AssessmentApplicationService assessmentService;
    private final AssessmentChecksumService checksumService;
    private final ObjectMapper objectMapper;
    private final ReportStorageService storageService;
    private final PdfReportRenderer renderer = new PdfReportRenderer();

    public ReportDashboardService(
            ReportDashboardRepository repository,
            AssessmentApplicationService assessmentService,
            AssessmentChecksumService checksumService,
            ObjectMapper objectMapper,
            ReportStorageService storageService) {
        this.repository = repository;
        this.assessmentService = assessmentService;
        this.checksumService = checksumService;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    public Map<String, Object> overview(String scopeType, String scopeId) {
        Scope scope = Scope.parse(scopeType, scopeId);
        List<Map<String, Object>> rows = repository.dashboardRows(scope);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("communityCount", rows.stream().map(row -> row.get("communityId")).distinct().count());
        summary.put("buildingCount", (long) rows.size());
        summary.put("assessedBuildingCount", count(rows, row -> !"NO_RESULT".equals(row.get("freshness"))));
        summary.put("highRiskCount", count(rows,
                row -> inList(row.get("riskLevel"), List.of("HIGH", "VERY_HIGH"))));
        summary.put("lowConfidenceCount", count(rows,
                row -> number(row.get("confidenceScore")) < 60 && row.get("riskScore") != null));
        summary.put("highPriorityCount", count(rows,
                row -> inList(row.get("priorityLevel"), List.of("P1", "P2"))));
        summary.put("staleCount", count(rows, row -> "STALE".equals(row.get("freshness"))));
        summary.put("noResultCount", count(rows, row -> "NO_RESULT".equals(row.get("freshness"))));

        List<Map<String, Object>> topRisk = rows.stream()
                .filter(row -> row.get("riskScore") != null)
                .sorted(Comparator.comparingDouble(
                                (Map<String, Object> row) -> number(row.get("riskScore")))
                        .reversed())
                .limit(10)
                .toList();
        List<Map<String, Object>> topPriority = rows.stream()
                .filter(row -> row.get("priorityScore") != null)
                .sorted(Comparator.comparingDouble(
                                (Map<String, Object> row) -> number(row.get("priorityScore")))
                        .reversed()
                        .thenComparing(row -> String.valueOf(row.get("buildingId"))))
                .limit(10)
                .toList();
        List<Map<String, Object>> reviewRequired = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("needManualReview"))
                        || (row.get("confidenceScore") != null
                        && number(row.get("confidenceScore")) < 60))
                .sorted(Comparator.comparingDouble(
                                (Map<String, Object> row) -> number(row.get("riskScore")))
                        .reversed())
                .limit(10)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeKey", scope.key());
        result.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC));
        result.put("summary", summary);
        result.put("riskDistribution", distribution(
                rows,
                "riskLevel",
                List.of("LOW", "MEDIUM", "HIGH", "VERY_HIGH"),
                List.of("低", "中", "高", "很高")));
        result.put("completenessDistribution", completenessDistribution(rows));
        result.put("priorityDistribution", distribution(
                rows,
                "priorityLevel",
                List.of("P1", "P2", "P3", "P4"),
                List.of("优先一", "优先二", "优先三", "优先四")));
        result.put("freshnessDistribution", distribution(
                rows,
                "freshness",
                List.of("CURRENT", "STALE", "NO_RESULT"),
                List.of("当前", "已过期", "无结果")));
        result.put("topRiskBuildings", topRisk);
        result.put("topPriorityBuildings", topPriority);
        result.put("reviewRequiredBuildings", reviewRequired);
        result.put("disclaimer", DISCLAIMER);
        return result;
    }

    public Map<String, Object> riskMap(String scopeType, String scopeId) {
        Scope scope = Scope.parse(scopeType, scopeId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeKey", scope.key());
        result.put("generatedAt", OffsetDateTime.now(ZoneOffset.UTC));
        result.put("buildings", repository.dashboardRows(scope));
        result.put("disclaimer", DISCLAIMER);
        return result;
    }

    public Map<String, Object> preview(UUID buildingId) {
        Map<String, Object> assessment = assessmentService.current(buildingId);
        String freshness = String.valueOf(assessment.getOrDefault("freshness", "NO_RESULT"));
        if ("NO_RESULT".equals(freshness)) {
            throw new InvalidRequestException(
                    "REPORT_ASSESSMENT_NOT_FOUND", "楼栋尚无正式评分结果，无法生成报告");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshotVersion", "1.0");
        snapshot.put("building", repository.building(buildingId));
        snapshot.put("assessment", assessment);
        snapshot.put("inspections", repository.inspections(buildingId));
        snapshot.put("evidence", repository.evidence(buildingId));
        snapshot.put("aiEvidence", repository.aiEvidence(buildingId));
        snapshot.put("responsibilityBoundary", DISCLAIMER);
        String checksum = checksumService.checksum(snapshot);
        repository.markStale(buildingId, TEMPLATE_VERSION, checksum);

        Map<String, Object> building = castMap(snapshot.get("building"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", buildingId);
        result.put("buildingCode", building.get("buildingCode"));
        result.put("buildingName", building.get("buildingName"));
        result.put("communityName", building.get("communityName"));
        result.put("freshness", freshness);
        result.put("sourceChecksum", checksum);
        result.put("templateVersion", TEMPLATE_VERSION);
        result.put("sections", snapshot);
        result.put("warnings", "STALE".equals(freshness)
                ? List.of("当前评分已过期，报告仅可用于历史研判，建议先重新计算评分")
                : List.of());
        result.put("disclaimer", DISCLAIMER);
        return result;
    }

    /**
     * 报告状态写入和文件存储不放在同一数据库事务中。
     * 这样文件生成失败后，FAILED 状态仍可独立提交并用于排障。
     */
    public Map<String, Object> generate(UUID buildingId, boolean force, UUID generatedBy) {
        Map<String, Object> preview = preview(buildingId);
        String checksum = String.valueOf(preview.get("sourceChecksum"));
        String idempotencyKey = buildingId + ":" + TEMPLATE_VERSION + ":" + checksum;
        if (!force) {
            Optional<Map<String, Object>> reusable = repository.findReusable(idempotencyKey);
            if (reusable.isPresent()) {
                return generationResponse(reusable.get(), true);
            }
        } else {
            idempotencyKey += ":" + UUID.randomUUID();
        }

        UUID reportId = UUID.randomUUID();
        String reportCode = "RPT-" + OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
                + reportId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Map<String, Object> snapshot = castMap(preview.get("sections"));
        Map<String, Object> assessment = castMap(snapshot.get("assessment"));
        UUID communityId = uuid(castMap(snapshot.get("building")).get("communityId"));
        UUID riskAssessmentId = uuidOrNull(castMap(assessment.get("risk")).get("assessmentId"));
        UUID renewalPriorityId = firstPriorityId(assessment.get("renewalPriorities"));
        Map<String, Object> summary = reportSummary(assessment);
        repository.createGenerating(
                reportId,
                reportCode,
                buildingId,
                communityId,
                riskAssessmentId,
                renewalPriorityId,
                checksum,
                idempotencyKey,
                json(snapshot),
                json(summary),
                generatedBy,
                string(castMap(assessment.get("risk")).get("ruleVersion")),
                firstPriorityRule(assessment.get("renewalPriorities")));

        long started = System.nanoTime();
        try {
            byte[] pdf = renderer.render(reportCode, snapshot, DISCLAIMER);
            StoredReport stored = storageService.save(
                    buildingId, reportId, reportCode, pdf, generatedBy);
            long duration = (System.nanoTime() - started) / 1_000_000L;
            repository.complete(reportId, stored, duration);
            return generationResponse(repository.report(reportId), false);
        } catch (RuntimeException ex) {
            repository.fail(reportId, "REPORT_GENERATION_FAILED", safeMessage(ex));
            throw ex;
        }
    }

    public Map<String, Object> list(
            UUID buildingId, UUID communityId, String status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        String normalizedStatus = normalizeStatus(status);
        List<Map<String, Object>> content = repository.list(
                        buildingId, communityId, normalizedStatus, safePage, safeSize)
                .stream()
                .map(this::reportListRow)
                .toList();
        long total = repository.countReports(buildingId, communityId, normalizedStatus);
        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("page", safePage);
        pageData.put("size", safeSize);
        pageData.put("totalElements", total);
        pageData.put("totalPages", total == 0 ? 0 : (total + safeSize - 1) / safeSize);
        return Map.of("content", content, "page", pageData);
    }

    private Map<String, Object> reportListRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of(
                "reportId",
                "reportCode",
                "buildingId",
                "buildingCode",
                "buildingName",
                "communityId",
                "communityName",
                "reportStatus",
                "reportFormat",
                "templateVersion",
                "sourceChecksum",
                "riskLevel",
                "priorityLevel",
                "generatedAt",
                "createdAt")) {
            result.put(field, row.get(field));
        }
        return result;
    }

    public Map<String, Object> detail(UUID reportId) {
        Map<String, Object> row = repository.report(reportId);
        row.put("reportSummary", parseJson(row.remove("reportSummaryJson")));
        row.put("reportSnapshot", parseJson(row.remove("reportSnapshotJson")));
        row.put("downloadUrl", "/api/v1/risk-reports/" + reportId + "/download");
        return row;
    }

    public ReportDownload download(UUID reportId) {
        Map<String, Object> row = repository.report(reportId);
        if (!"GENERATED".equals(row.get("reportStatus"))
                && !"STALE".equals(row.get("reportStatus"))) {
            throw new InvalidRequestException("REPORT_FILE_NOT_READY", "报告文件尚未生成完成");
        }
        String objectKey = string(row.get("objectKey"));
        String bucket = string(row.get("bucketName"));
        String provider = string(row.get("storageProvider"));
        if (objectKey == null || bucket == null) {
            throw new ResourceNotFoundException("REPORT_FILE_NOT_FOUND", "报告文件不存在");
        }
        byte[] bytes = storageService.read(bucket, objectKey, provider);
        String filename = string(row.get("originalFilename"));
        if (filename == null || filename.isBlank()) {
            filename = string(row.get("reportCode")) + ".pdf";
        }
        return new ReportDownload((UUID) row.get("buildingId"), filename, bytes);
    }

    private Map<String, Object> generationResponse(Map<String, Object> row, boolean reused) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", row.get("reportId"));
        result.put("reportCode", row.get("reportCode"));
        result.put("reportStatus", row.get("reportStatus"));
        result.put("reportFormat", row.get("reportFormat"));
        result.put("templateVersion", row.get("templateVersion"));
        result.put("sourceChecksum", row.get("sourceChecksum"));
        result.put("reused", reused);
        result.put("generatedAt", row.get("generatedAt"));
        result.put("warnings", List.of());
        return result;
    }

    private List<Map<String, Object>> distribution(
            List<Map<String, Object>> rows,
            String field,
            List<String> codes,
            List<String> labels) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            result.add(Map.of(
                    "code", code,
                    "label", labels.get(i),
                    "count", count(rows, row -> code.equals(row.get(field)))));
        }
        return result;
    }

    private List<Map<String, Object>> completenessDistribution(
            List<Map<String, Object>> rows) {
        long insufficient = count(rows,
                row -> valueInRange(row.get("completenessScore"), 0, 40));
        long limited = count(rows,
                row -> valueInRange(row.get("completenessScore"), 40, 60));
        long good = count(rows,
                row -> valueInRange(row.get("completenessScore"), 60, 80));
        long excellent = count(rows,
                row -> valueInRange(row.get("completenessScore"), 80, 101));
        return List.of(
                Map.of("code", "INSUFFICIENT", "label", "不足", "count", insufficient),
                Map.of("code", "LIMITED", "label", "有限", "count", limited),
                Map.of("code", "GOOD", "label", "良好", "count", good),
                Map.of("code", "EXCELLENT", "label", "优秀", "count", excellent));
    }

    private long count(
            List<Map<String, Object>> rows,
            java.util.function.Predicate<Map<String, Object>> predicate) {
        return rows.stream().filter(predicate).count();
    }

    private boolean valueInRange(Object value, double start, double end) {
        return value != null && number(value) >= start && number(value) < end;
    }

    private boolean inList(Object value, List<String> candidates) {
        return value != null && candidates.contains(value);
    }

    private double number(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private Map<String, Object> reportSummary(Map<String, Object> assessment) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> completeness = castMap(assessment.get("completeness"));
        Map<String, Object> risk = castMap(assessment.get("risk"));
        Map<String, Object> priority = firstMap(assessment.get("renewalPriorities"));
        result.put("completenessScore", completeness.get("completenessScore"));
        result.put("completenessLevel", completeness.get("completenessLevel"));
        result.put("riskScore", risk.get("riskScore"));
        result.put("riskLevel", risk.get("riskLevel"));
        result.put("confidenceScore", risk.get("confidenceScore"));
        result.put("priorityScore", priority.get("priorityScore"));
        result.put("priorityLevel", priority.get("priorityLevel"));
        return result;
    }

    private UUID firstPriorityId(Object value) {
        return uuidOrNull(firstMap(value).get("priorityId"));
    }

    private String firstPriorityRule(Object value) {
        return string(firstMap(value).get("ruleVersion"));
    }

    private Map<String, Object> firstMap(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return castMap(list.get(0));
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("报告快照序列化失败", ex);
        }
    }

    private Object parseJson(Object value) {
        if (value == null) return Map.of();
        try {
            return objectMapper.readValue(String.valueOf(value), Object.class);
        } catch (JsonProcessingException ex) {
            return Map.of("raw", String.valueOf(value));
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("GENERATING", "GENERATED", "FAILED", "STALE")
                .contains(normalized)) {
            throw new InvalidRequestException("REPORT_STATUS_INVALID", "报告状态参数无效");
        }
        return normalized;
    }

    private UUID uuid(Object value) {
        UUID result = uuidOrNull(value);
        if (result == null) {
            throw new IllegalStateException("报告快照缺少必要 UUID");
        }
        return result;
    }

    private UUID uuidOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String safeMessage(Throwable ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }
}

record ReportDownload(UUID buildingId, String filename, byte[] bytes) {}
