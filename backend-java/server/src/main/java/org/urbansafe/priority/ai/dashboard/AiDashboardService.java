package org.urbansafe.priority.ai.dashboard;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.report.controller.ReportDashboardService;

/** AI 工作台与态势大屏只读聚合服务。 */
@Service
public class AiDashboardService {

    private static final long SNAPSHOT_CACHE_NANOS = 8_000_000_000L;
    private static final long ACTIVITY_CACHE_NANOS = 4_000_000_000L;
    private static final int MAX_ATTENTION_ITEMS = 8;

    private final AiDashboardRepository repository;
    private final ReportDashboardService reportDashboardService;
    private final Map<Integer, CacheEntry<Map<String, Object>>> activityCache = new ConcurrentHashMap<>();
    private volatile CacheEntry<DashboardSnapshot> snapshotCache;

    public AiDashboardService(
            AiDashboardRepository repository,
            ReportDashboardService reportDashboardService) {
        this.repository = repository;
        this.reportDashboardService = reportDashboardService;
    }

    public Map<String, Object> overview() {
        return snapshot().overview();
    }

    public List<Map<String, Object>> buildings() {
        return snapshot().buildings();
    }

    public Map<String, Object> activity(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        CacheEntry<Map<String, Object>> cached = activityCache.get(limit);
        if (cached != null && cached.validFor(ACTIVITY_CACHE_NANOS)) {
            return cached.value();
        }

        List<Map<String, Object>> items = repository.activityRows(limit).stream()
                .map(this::activityItem)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("generatedAt", OffsetDateTime.now());
        response.put("items", items);
        activityCache.put(limit, new CacheEntry<>(System.nanoTime(), response));
        return response;
    }

    private DashboardSnapshot snapshot() {
        CacheEntry<DashboardSnapshot> cached = snapshotCache;
        if (cached != null && cached.validFor(SNAPSHOT_CACHE_NANOS)) {
            return cached.value();
        }
        synchronized (this) {
            cached = snapshotCache;
            if (cached != null && cached.validFor(SNAPSHOT_CACHE_NANOS)) {
                return cached.value();
            }
            DashboardSnapshot next = buildSnapshot();
            snapshotCache = new CacheEntry<>(System.nanoTime(), next);
            return next;
        }
    }

    private DashboardSnapshot buildSnapshot() {
        Map<String, Object> riskMap = reportDashboardService.riskMap("ALL", null);
        List<Map<String, Object>> riskRows = maps(riskMap.get("buildings"));
        Map<String, Map<String, Object>> aiByBuilding = indexByBuilding(repository.buildingAiRows());
        Map<String, List<Map<String, Object>>> findingsByBuilding = groupFindings(repository.latestFindings());

        List<Map<String, Object>> buildings = new ArrayList<>();
        for (Map<String, Object> riskRow : riskRows) {
            buildings.add(buildBuilding(
                    riskRow,
                    aiByBuilding.getOrDefault(key(riskRow.get("buildingId")), Map.of()),
                    findingsByBuilding.getOrDefault(key(riskRow.get("buildingId")), List.of())));
        }

        buildings.sort(this::compareAttention);
        Map<String, Object> rawMetrics = repository.metrics();
        Map<String, Object> metrics = new LinkedHashMap<>();
        long buildingCount = buildings.size();
        long aiAnalyzedBuildingCount = longValue(rawMetrics.get("aiAnalyzedBuildingCount"));
        metrics.put("buildingCount", buildingCount);
        metrics.put("aiAnalyzedBuildingCount", aiAnalyzedBuildingCount);
        metrics.put("aiAnalyzedImageCount", longValue(rawMetrics.get("aiAnalyzedImageCount")));
        metrics.put("detectionCount", longValue(rawMetrics.get("detectionCount")));
        metrics.put("highRiskCount", buildings.stream()
                .filter(item -> isHighRisk(string(item.get("riskLevel"))))
                .count());
        metrics.put("pendingReviewCount", longValue(rawMetrics.get("pendingReviewCount")));
        metrics.put("inspectionAttentionCount", buildings.stream()
                .filter(this::hasAiFindings)
                .count());
        metrics.put("dataIssueCount", buildings.stream()
                .filter(this::hasDataIssue)
                .count());
        metrics.put("analysisCoverageRate", buildingCount == 0
                ? 0.0
                : Math.round(aiAnalyzedBuildingCount * 10_000.0 / buildingCount) / 100.0);

        Map<String, Object> today = normalizeToday(repository.todayMetrics());
        List<Map<String, Object>> attention = buildings.stream()
                .filter(item -> !"NONE".equals(item.get("aiAttentionLevel")))
                .limit(MAX_ATTENTION_ITEMS)
                .toList();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("generatedAt", OffsetDateTime.now());
        overview.put("metrics", metrics);
        overview.put("today", today);
        overview.put("attention", attention);
        return new DashboardSnapshot(overview, List.copyOf(buildings));
    }

    private Map<String, Object> buildBuilding(
            Map<String, Object> riskRow,
            Map<String, Object> aiRow,
            List<Map<String, Object>> findings) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of(
                "buildingId", "buildingCode", "buildingName", "communityId", "communityName",
                "longitude", "latitude", "riskLevel", "riskScore", "priorityLevel", "freshness",
                "needManualReview")) {
            result.put(field, riskRow.get(field));
        }

        long pendingReviewCount = longValue(aiRow.get("pendingReviewCount"));
        long visualCount = longValue(aiRow.get("visualCount"));
        long inspectionCount = longValue(aiRow.get("inspectionCount"));
        long archiveCount = longValue(aiRow.get("archiveCount"));
        boolean formalReview = Boolean.TRUE.equals(riskRow.get("needManualReview"));
        String riskLevel = string(riskRow.get("riskLevel"));
        String freshness = string(riskRow.get("freshness"));

        List<String> reasons = new ArrayList<>();
        if (isHighRisk(riskLevel)) reasons.add("正式高风险");
        if (!findings.isEmpty()) reasons.add("新增 AI 病害");
        if (pendingReviewCount > 0) reasons.add("待人工复核");
        if (formalReview) reasons.add("正式评分需人工复核");
        if ("STALE".equals(freshness)) reasons.add("风险结果已过期");
        if ("NO_RESULT".equals(freshness)) reasons.add("暂无正式风险结果");
        if (archiveCount == 0) reasons.add("档案资料不足");

        String attentionLevel = attentionLevel(
                riskLevel,
                pendingReviewCount,
                formalReview,
                findings.isEmpty(),
                freshness,
                visualCount);
        result.put("aiAttentionLevel", attentionLevel);
        result.put("aiAttentionReasons", reasons);
        result.put("latestAiSummary", aiRow.get("latestAiSummary"));
        result.put("latestAiAt", aiRow.get("latestAiAt"));
        result.put("latestInspectionAt", aiRow.get("latestInspectionAt"));
        result.put("pendingReviewCount", pendingReviewCount);
        result.put("findings", findings.stream().map(this::findingView).toList());
        result.put("evidenceCounts", Map.of(
                "visual", visualCount,
                "inspection", inspectionCount,
                "archive", archiveCount,
                "formalRisk", riskRow.get("riskScore") == null ? 0 : 1));
        return result;
    }

    private Map<String, Object> findingView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classCode", row.get("classCode"));
        result.put("className", row.get("className"));
        result.put("count", longValue(row.get("count")));
        result.put("maxConfidence", row.get("maxConfidence"));
        return result;
    }

    private Map<String, Object> activityItem(Map<String, Object> row) {
        String type = string(row.get("eventType"));
        String objectName = objectName(row);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(row.get("eventId")));
        result.put("occurredAt", row.get("occurredAt"));
        result.put("type", type);
        result.put("status", row.get("status"));
        result.put("buildingId", row.get("buildingId"));
        result.put("buildingName", row.get("buildingName"));
        result.put("communityName", row.get("communityName"));

        if ("AI_REVIEW".equals(type)) {
            result.put("title", "完成 " + objectName + " 人工复核");
            result.put("description", reviewDescription(string(row.get("status"))));
            return result;
        }

        String status = string(row.get("status"));
        long detections = longValue(row.get("detectionCount"));
        if ("SUCCEEDED".equals(status)) {
            result.put("title", "完成 " + objectName + " 图片分析");
            result.put("description", detections > 0
                    ? "发现疑似病害 " + detections + " 处"
                    : "AI 视觉识别已完成，未形成可展示的病害候选");
        } else if ("PENDING".equals(status) || "RUNNING".equals(status)) {
            result.put("title", "正在分析 " + objectName + " 巡检图片");
            result.put("description", "后台高精度识别正在进行");
        } else if ("REJECTED".equals(status)) {
            result.put("title", objectName + " 图片未进入病害研判");
            result.put("description", "图片不适用于当前视觉识别");
        } else {
            result.put("title", objectName + " 图片分析未完成");
            result.put("description", "AI 辅助能力异常，基础业务不受影响");
        }
        return result;
    }

    private Map<String, Object> normalizeToday(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String metricKey : List.of(
                "totalAnalyses", "succeeded", "running", "failed",
                "crackCount", "spallingCount", "waterStainCount", "otherDetectionCount")) {
            result.put(metricKey, longValue(source.get(metricKey)));
        }
        return result;
    }

    private Map<String, Map<String, Object>> indexByBuilding(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(key(row.get("buildingId")), row);
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> groupFindings(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.computeIfAbsent(key(row.get("buildingId")), ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private String attentionLevel(
            String riskLevel,
            long pendingReviewCount,
            boolean formalReview,
            boolean noFindings,
            String freshness,
            long visualCount) {
        if (isHighRisk(riskLevel) || (pendingReviewCount > 0 && !noFindings)) return "HIGH";
        if (pendingReviewCount > 0 || formalReview || !noFindings
                || "STALE".equals(freshness) || "NO_RESULT".equals(freshness)) return "MEDIUM";
        if (visualCount > 0) return "LOW";
        return "NONE";
    }

    private int compareAttention(Map<String, Object> left, Map<String, Object> right) {
        int attention = Integer.compare(
                attentionWeight(string(right.get("aiAttentionLevel"))),
                attentionWeight(string(left.get("aiAttentionLevel"))));
        if (attention != 0) return attention;
        int risk = Double.compare(doubleValue(right.get("riskScore")), doubleValue(left.get("riskScore")));
        if (risk != 0) return risk;
        return key(left.get("buildingId")).compareTo(key(right.get("buildingId")));
    }

    private int attentionWeight(String level) {
        return switch (level == null ? "NONE" : level) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private boolean hasAiFindings(Map<String, Object> item) {
        return item.get("findings") instanceof List<?> findings && !findings.isEmpty();
    }

    private boolean hasDataIssue(Map<String, Object> item) {
        if (!(item.get("aiAttentionReasons") instanceof List<?> reasons)) return false;
        return reasons.contains("风险结果已过期")
                || reasons.contains("暂无正式风险结果")
                || reasons.contains("档案资料不足");
    }

    private boolean isHighRisk(String riskLevel) {
        return "HIGH".equals(riskLevel) || "VERY_HIGH".equals(riskLevel);
    }

    private String reviewDescription(String status) {
        return switch (status == null ? "" : status) {
            case "CONFIRMED" -> "人工已确认 AI 发现";
            case "CORRECTED" -> "人工已修正 AI 结果";
            case "REJECTED" -> "人工已排除 AI 候选";
            default -> "人工复核状态已更新";
        };
    }

    private String objectName(Map<String, Object> row) {
        String community = string(row.get("communityName"));
        String building = string(row.get("buildingName"));
        if (community != null && building != null) return community + " · " + building;
        if (building != null) return building;
        return "当前楼栋";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> maps(Object value) {
        if (value instanceof List<?> list) return (List<Map<String, Object>>) list;
        return List.of();
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : -1.0;
    }

    private String string(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String key(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record DashboardSnapshot(
            Map<String, Object> overview,
            List<Map<String, Object>> buildings) {
    }

    private record CacheEntry<T>(long createdAtNanos, T value) {
        boolean validFor(long durationNanos) {
            return System.nanoTime() - createdAtNanos < durationNanos;
        }
    }
}
