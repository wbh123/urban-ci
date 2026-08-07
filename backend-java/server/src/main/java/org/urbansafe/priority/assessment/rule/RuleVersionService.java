package org.urbansafe.priority.assessment.rule;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

/** 规则草稿、激活和查询服务。 */
@Service
public class RuleVersionService {

    private final RuleVersionRepository repository;
    private final AssessmentRuleValidator validator;
    private final RuleChecksumService checksumService;

    public RuleVersionService(
            RuleVersionRepository repository,
            AssessmentRuleValidator validator,
            RuleChecksumService checksumService) {
        this.repository = repository;
        this.validator = validator;
        this.checksumService = checksumService;
    }

    public List<Map<String, Object>> list(String ruleType, String status) {
        return repository.list(normalize(ruleType), normalize(status));
    }

    public Map<String, Object> get(UUID ruleId) {
        return repository.find(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASSESSMENT_RULE_NOT_FOUND", "评分规则不存在"));
    }

    public RuleSnapshot active(String ruleType) {
        return repository.findActive(ruleType)
                .orElseThrow(() -> new ResourceConflictException(
                        "ACTIVE_ASSESSMENT_RULE_MISSING",
                        "缺少激活的 " + ruleType + " 评分规则"));
    }

    @Transactional
    public Map<String, Object> createDraft(
            String ruleType, String versionCode, String ruleName,
            JsonNode content, UUID createdBy) {
        String normalizedType = normalize(ruleType);
        validator.validate(normalizedType, content);
        String checksum = checksumService.checksum(content);
        UUID id = UUID.randomUUID();
        try {
            repository.insertDraft(
                    id, normalizedType, versionCode.trim(), ruleName.trim(),
                    content, checksum, createdBy);
        } catch (DuplicateKeyException ex) {
            throw new ResourceConflictException(
                    "ASSESSMENT_RULE_VERSION_CONFLICT",
                    "同类型规则版本编码已存在");
        }
        return get(id);
    }

    @Transactional
    public Map<String, Object> activate(UUID ruleId) {
        Map<String, Object> draft = repository.lock(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ASSESSMENT_RULE_NOT_FOUND", "评分规则不存在"));
        if (!"DRAFT".equals(String.valueOf(draft.get("status")))) {
            throw new ResourceConflictException(
                    "ASSESSMENT_RULE_NOT_DRAFT", "只有 DRAFT 规则可以激活");
        }
        String ruleType = String.valueOf(draft.get("ruleType"));
        UUID retiredRuleId = repository.retireActive(ruleType);
        repository.activate(ruleId);
        long staleCount = repository.markCurrentAssessmentsStale(
                ruleType, "RULE_CHANGED:" + draft.get("versionCode"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeRule", get(ruleId));
        result.put("retiredRuleId", retiredRuleId);
        result.put("staleAssessmentCount", staleCount);
        return result;
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
