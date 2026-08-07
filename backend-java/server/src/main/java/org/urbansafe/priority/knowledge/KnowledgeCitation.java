package org.urbansafe.priority.knowledge;

import java.util.UUID;

/** 答案引用的可追溯知识片段。 */
public record KnowledgeCitation(
        UUID citationId,
        UUID documentId,
        String documentCode,
        String documentTitle,
        String documentVersion,
        UUID chunkId,
        String sectionTitle,
        Integer pageNumber,
        String excerpt,
        int rank,
        double score) {
}
