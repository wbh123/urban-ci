package org.urbansafe.priority.evidence.result;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 楼栋证据详情的内部业务结果。 */
public record EvidenceDetailResult(UUID id, UUID buildingId, String evidenceType, String title,
        String description, OffsetDateTime occurredAt, String source, String reliabilityLevel,
        JsonNode evidenceData, UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        Long version) { }
