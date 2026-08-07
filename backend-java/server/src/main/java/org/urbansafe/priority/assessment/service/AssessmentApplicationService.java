package org.urbansafe.priority.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.urbansafe.priority.assessment.calculator.RenewalPriorityCalculator;
import org.urbansafe.priority.assessment.checksum.AssessmentChecksumService;
import org.urbansafe.priority.assessment.checksum.AssessmentInputCanonicalizer;
import org.urbansafe.priority.assessment.input.AssessmentInputAssembler;
import org.urbansafe.priority.assessment.model.AssessmentResults.CompletenessResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RenewalResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.assessment.repository.AssessmentResultRepository;
import org.urbansafe.priority.assessment.rule.RuleVersionService;
import org.urbansafe.priority.common.exception.InvalidRequestException;

/** 第四阶段评分应用编排：统一输入、幂等、历史、批量和稳定排名。 */
@Service
public class AssessmentApplicationService {

    public static final String ENGINE_VERSION = "phase4-rule-engine-v1";

    private final AssessmentInputAssembler inputAssembler;
    private final AssessmentInputCanonicalizer canonicalizer;
    private final AssessmentChecksumService checksumService;
    private final RuleVersionService ruleService;
    private final CompletenessAssessmentService completenessService;
    private final RiskAssessmentService riskService;
    private final RenewalPriorityCalculator renewalCalculator;
    private final AssessmentResultRepository repository;
    private final RenewalRankingService rankingService;
    private final TransactionTemplate transactionTemplate;

    public AssessmentApplicationService(
            AssessmentInputAssembler inputAssembler,
            AssessmentInputCanonicalizer canonicalizer,
            AssessmentChecksumService checksumService,
            RuleVersionService ruleService,
            CompletenessAssessmentService completenessService,
            RiskAssessmentService riskService,
            RenewalPriorityCalculator renewalCalculator,
            AssessmentResultRepository repository,
            RenewalRankingService rankingService,
            PlatformTransactionManager transactionManager) {
        this.inputAssembler = inputAssembler;
        this.canonicalizer = canonicalizer;
        this.checksumService = checksumService;
        this.ruleService = ruleService;
        this.completenessService = completenessService;
        this.riskService = riskService;
        this.renewalCalculator = renewalCalculator;
        this.repository = repository;
        this.rankingService = rankingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> calculate(
            UUID buildingId, boolean force, Set<String> rankingScopes,
            String triggerType, UUID triggeredBy) {
        return transactionTemplate.execute(status -> calculateInternal(
                buildingId, force, rankingScopes, triggerType, triggeredBy));
    }

    public Map<String, Object> current(UUID buildingId) {
        return repository.currentBuilding(buildingId);
    }

    public Map<String, Object> summary(UUID buildingId) {
        Map<String, Object> current = repository.currentBuilding(buildingId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("buildingId", current.get("buildingId"));
        result.put("buildingCode", current.get("buildingCode"));
        result.put("buildingName", current.get("buildingName"));
        result.put("communityId", current.get("communityId"));
        result.put("communityName", current.get("communityName"));
        result.put("freshness", current.get("freshness"));
        result.put("completeness", completenessSummary(current.get("completeness")));
        result.put("risk", riskSummary(current.get("risk")));
        result.put("disclaimer", AssessmentResultRepository.disclaimer());
        return result;
    }

    public Map<String, Object> history(
            UUID buildingId, String assessmentType, int page, int size) {
        String normalizedType = normalizeAssessmentType(assessmentType);
        List<Map<String, Object>> content = repository.history(buildingId, normalizedType, page, size);
        long total = repository.historyCount(buildingId, normalizedType);
        return Map.of(
                "content", content,
                "page", page(page, size, total));
    }

    public Map<String, Object> ranking(
            String scopeType, String scopeId, String priorityLevel,
            String riskLevel, int page, int size) {
        String scopeKey = scopeKey(scopeType, scopeId);
        List<Map<String, Object>> filtered = rankingService.current(scopeKey).stream()
                .filter(row -> priorityLevel == null
                        || priorityLevel.equalsIgnoreCase(String.valueOf(row.get("priorityLevel"))))
                .filter(row -> riskLevel == null
                        || riskLevel.equalsIgnoreCase(String.valueOf(row.get("riskLevel"))))
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return Map.of(
                "scopeKey", scopeKey,
                "content", filtered.subList(from, to),
                "page", page(page, size, filtered.size()),
                "disclaimer", AssessmentResultRepository.disclaimer());
    }

    public Map<String, Object> batch(
            String scopeType, String scopeId, boolean force, int maxBuildings, UUID triggeredBy) {
        String normalizedScope = normalizeScope(scopeType);
        String normalizedScopeKey = scopeKey(normalizedScope, scopeId);
        if (maxBuildings < 1 || maxBuildings > 500) {
            throw new InvalidRequestException(
                    "ASSESSMENT_BATCH_LIMIT_INVALID", "批量重算楼栋数量必须位于 1 到 500");
        }
        List<UUID> buildingIds = repository.buildingIds(normalizedScope, scopeId, maxBuildings);
        List<Map<String, Object>> items = new ArrayList<>();
        int success = 0;
        int reused = 0;
        int failed = 0;
        for (UUID buildingId : buildingIds) {
            try {
                Map<String, Object> result = calculate(
                        buildingId, force, Set.of("COMMUNITY", "REGION", "ALL"),
                        "BATCH", triggeredBy);
                boolean wasReused = Boolean.TRUE.equals(result.get("reused"));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("buildingId", buildingId);
                item.put("status", wasReused ? "REUSED" : "SUCCESS");
                item.put("calculationBatchId", result.get("calculationBatchId"));
                items.add(item);
                if (wasReused) reused++; else success++;
            } catch (RuntimeException ex) {
                failed++;
                items.add(Map.of(
                        "buildingId", buildingId,
                        "status", "FAILED",
                        "errorCode", "ASSESSMENT_CALCULATION_FAILED",
                        "message", safeMessage(ex)));
            }
        }
        return Map.of(
                "scopeKey", normalizedScopeKey,
                "requestedCount", buildingIds.size(),
                "successCount", success,
                "reusedCount", reused,
                "skippedCount", 0,
                "failedCount", failed,
                "items", items,
                "disclaimer", AssessmentResultRepository.disclaimer());
    }

    private Map<String, Object> calculateInternal(
            UUID buildingId, boolean force, Set<String> requestedScopes,
            String triggerType, UUID triggeredBy) {
        repository.lockBuilding(buildingId);
        BuildingAssessmentInput input = inputAssembler.assemble(buildingId);
        RuleSnapshot completenessRule = ruleService.active("COMPLETENESS");
        RuleSnapshot riskRule = ruleService.active("RISK");
        RuleSnapshot renewalRule = ruleService.active("RENEWAL");
        Set<String> scopeKeys = resolveScopeKeys(input, requestedScopes);

        Map<String, Object> completenessEnvelope = Map.of(
                "buildingId", buildingId, "input", input,
                "ruleVersion", completenessRule, "engineVersion", ENGINE_VERSION);
        String completenessChecksum = checksumService.checksum(completenessEnvelope);
        JsonNode completenessSnapshot = canonicalizer.canonicalTree(completenessEnvelope);

        Map<String, Object> riskEnvelope = Map.of(
                "buildingId", buildingId, "input", input,
                "completenessRuleVersion", completenessRule.versionCode(),
                "riskRuleVersion", riskRule, "engineVersion", ENGINE_VERSION);
        String riskChecksum = checksumService.checksum(riskEnvelope);
        JsonNode riskSnapshot = canonicalizer.canonicalTree(riskEnvelope);

        boolean reusable = !force
                && repository.reusableCompleteness(buildingId, completenessChecksum,
                        completenessRule.ruleId(), ENGINE_VERSION).isPresent()
                && repository.reusableRisk(buildingId, riskChecksum,
                        riskRule.ruleId(), ENGINE_VERSION).isPresent();
        if (reusable) {
            for (String scopeKey : scopeKeys) {
                String renewalChecksum = renewalChecksum(input, riskRule, renewalRule, scopeKey);
                if (repository.reusableRenewal(buildingId, scopeKey, renewalChecksum,
                        renewalRule.ruleId(), ENGINE_VERSION).isEmpty()) {
                    reusable = false;
                    break;
                }
            }
        }
        if (reusable) {
            Map<String, Object> current = repository.currentBuilding(buildingId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("buildingId", buildingId);
            response.put("calculationBatchId", currentBatchId(current));
            response.put("reused", true);
            response.put("completeness", current.get("completeness"));
            response.put("risk", current.get("risk"));
            response.put("renewalPriorities", current.get("renewalPriorities"));
            response.put("warnings", List.of());
            response.put("excludedEvidenceCount", input.excludedAiEvidence().size());
            response.put("disclaimer", AssessmentResultRepository.disclaimer());
            return response;
        }

        UUID batchId = UUID.randomUUID();
        CompletenessResult completeness = completenessService.calculate(input, completenessRule);
        UUID completenessId = repository.saveCompleteness(
                buildingId, batchId, completenessRule, ENGINE_VERSION,
                normalizeTrigger(triggerType), triggeredBy,
                completenessSnapshot, completenessChecksum, completeness);

        RiskResult risk = riskService.calculate(input, riskRule, completeness.score());
        UUID riskId = repository.saveRisk(
                buildingId, completenessId, batchId, riskRule, ENGINE_VERSION,
                normalizeTrigger(triggerType), triggeredBy,
                riskSnapshot, riskChecksum, risk);

        for (String scopeKey : scopeKeys) {
            RenewalResult renewal = renewalCalculator.calculate(input, risk, renewalRule, riskRule);
            Map<String, Object> renewalEnvelope = renewalEnvelope(input, riskRule, renewalRule, scopeKey);
            repository.saveRenewal(
                    buildingId, riskId, batchId, renewalRule, ENGINE_VERSION,
                    normalizeTrigger(triggerType), triggeredBy, scopeKey,
                    scopeDescriptor(scopeKey), canonicalizer.canonicalTree(renewalEnvelope),
                    checksumService.checksum(renewalEnvelope), renewal);
        }
        for (String scopeKey : scopeKeys) {
            rankingService.refresh(scopeKey);
        }

        Map<String, Object> current = repository.currentBuilding(buildingId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("buildingId", buildingId);
        response.put("calculationBatchId", batchId);
        response.put("reused", false);
        response.put("completeness", current.get("completeness"));
        response.put("risk", current.get("risk"));
        response.put("renewalPriorities", current.get("renewalPriorities"));
        response.put("warnings", List.of());
        response.put("excludedEvidenceCount", input.excludedAiEvidence().size());
        response.put("disclaimer", AssessmentResultRepository.disclaimer());
        return response;
    }


    @SuppressWarnings("unchecked")
    private Map<String, Object> completenessSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completenessScore", map.get("completenessScore"));
        result.put("completenessLevel", map.get("completenessLevel"));
        result.put("missingItems", map.containsKey("missingItems") ? map.get("missingItems") : List.of());
        result.put("suggestions", map.containsKey("suggestions") ? map.get("suggestions") : List.of());
        return result;
    }

    private Map<String, Object> riskSummary(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskScore", map.get("riskScore"));
        result.put("riskLevel", map.get("riskLevel"));
        result.put("confidenceScore", map.get("confidenceScore"));
        result.put("confidenceLevel", map.get("confidenceLevel"));
        result.put("needManualReview", map.get("needManualReview"));
        result.put("needProfessionalInspection", map.get("needProfessionalInspection"));
        result.put("recommendations", map.containsKey("recommendations") ? map.get("recommendations") : List.of());
        return result;
    }


    private Set<String> resolveScopeKeys(BuildingAssessmentInput input, Set<String> requestedScopes) {
        Set<String> scopes = requestedScopes == null || requestedScopes.isEmpty()
                ? Set.of("COMMUNITY", "ALL") : requestedScopes;
        Set<String> keys = new LinkedHashSet<>();
        for (String scope : scopes) {
            switch (normalizeScope(scope)) {
                case "ALL" -> keys.add("ALL");
                case "COMMUNITY" -> keys.add("COMMUNITY:" + input.community().communityId());
                case "REGION" -> {
                    if (input.community().administrativeRegion() != null
                            && !input.community().administrativeRegion().isBlank()) {
                        keys.add("REGION:" + input.community().administrativeRegion());
                    }
                }
                default -> throw new IllegalStateException("Unexpected scope");
            }
        }
        return keys;
    }

    private String renewalChecksum(
            BuildingAssessmentInput input, RuleSnapshot riskRule,
            RuleSnapshot renewalRule, String scopeKey) {
        return checksumService.checksum(renewalEnvelope(input, riskRule, renewalRule, scopeKey));
    }

    private Map<String, Object> renewalEnvelope(
            BuildingAssessmentInput input, RuleSnapshot riskRule,
            RuleSnapshot renewalRule, String scopeKey) {
        return Map.of(
                "buildingId", input.building().buildingId(),
                "input", input,
                "riskRuleVersion", riskRule.versionCode(),
                "renewalRuleVersion", renewalRule,
                "rankingScopeKey", scopeKey,
                "engineVersion", ENGINE_VERSION);
    }

    private Map<String, Object> scopeDescriptor(String scopeKey) {
        int separator = scopeKey.indexOf(':');
        return separator < 0
                ? Map.of("scopeType", scopeKey)
                : Map.of("scopeType", scopeKey.substring(0, separator),
                        "scopeId", scopeKey.substring(separator + 1));
    }

    private String scopeKey(String scopeType, String scopeId) {
        String type = normalizeScope(scopeType);
        if ("ALL".equals(type)) return "ALL";
        if (scopeId == null || scopeId.isBlank()) {
            throw new InvalidRequestException("RANKING_SCOPE_ID_REQUIRED", type + " 排名必须提供 scopeId");
        }
        if ("COMMUNITY".equals(type)) {
            try {
                return "COMMUNITY:" + UUID.fromString(scopeId.trim());
            } catch (IllegalArgumentException ex) {
                throw new InvalidRequestException("COMMUNITY_ID_INVALID", "社区范围 ID 必须是 UUID");
            }
        }
        return "REGION:" + scopeId.trim();
    }

    private String normalizeScope(String value) {
        String normalized = value == null ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "REGION", "COMMUNITY").contains(normalized)) {
            throw new InvalidRequestException("RANKING_SCOPE_INVALID", "不支持的排名范围: " + value);
        }
        return normalized;
    }

    private String normalizeAssessmentType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("COMPLETENESS", "RISK", "RENEWAL").contains(normalized)) {
            throw new InvalidRequestException("ASSESSMENT_TYPE_INVALID", "不支持的评分类型: " + value);
        }
        return normalized;
    }

    private String normalizeTrigger(String value) {
        String normalized = value == null ? "MANUAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MANUAL", "BATCH", "DATA_CHANGE", "RULE_CHANGE", "DEMO_SEED")
                .contains(normalized)) {
            return "MANUAL";
        }
        return normalized;
    }

    private UUID currentBatchId(Map<String, Object> current) {
        Object completeness = current.get("completeness");
        if (completeness instanceof Map<?, ?> map && map.get("calculationBatchId") != null) {
            return UUID.fromString(String.valueOf(map.get("calculationBatchId")));
        }
        return UUID.randomUUID();
    }

    private Map<String, Object> page(int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(total / (double) size);
        return Map.of("page", page, "size", size,
                "totalElements", total, "totalPages", totalPages);
    }

    private String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null ? "评分计算失败" : ex.getMessage();
    }
}
