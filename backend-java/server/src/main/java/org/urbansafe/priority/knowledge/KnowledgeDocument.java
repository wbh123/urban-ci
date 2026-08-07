package org.urbansafe.priority.knowledge;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 已登记知识文档的稳定视图。 */
public record KnowledgeDocument(
        UUID documentId,
        String documentCode,
        String title,
        String documentVersion,
        String status,
        int chunkCount,
        OffsetDateTime createdAt) {
}
