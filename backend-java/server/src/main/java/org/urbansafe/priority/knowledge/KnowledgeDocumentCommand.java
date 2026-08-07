package org.urbansafe.priority.knowledge;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 登记经过审核的知识文档命令。 */
public record KnowledgeDocumentCommand(
        String documentCode,
        String title,
        String documentType,
        String documentVersion,
        String securityLevel,
        Set<String> roleScope,
        UUID communityId,
        UUID buildingId,
        String status,
        String sourceUri,
        String contentChecksum,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        Map<String, Object> metadata,
        List<KnowledgeChunkDraft> chunks,
        UUID createdBy) {

    public KnowledgeDocumentCommand {
        roleScope = roleScope == null ? Set.of() : Set.copyOf(roleScope);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
