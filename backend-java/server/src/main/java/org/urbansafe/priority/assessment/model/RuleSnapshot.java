package org.urbansafe.priority.assessment.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 激活规则或历史规则的不可变快照。 */
public record RuleSnapshot(
        UUID ruleId,
        String ruleType,
        String versionCode,
        String ruleName,
        JsonNode ruleContent,
        String checksum,
        String status,
        OffsetDateTime activatedAt) {
}
