package org.urbansafe.priority.knowledge;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 可审计的内部知识答案或受控拒答。 */
public record KnowledgeAnswer(
        UUID questionId,
        String status,
        String answer,
        boolean evidenceSufficient,
        List<KnowledgeCitation> citations,
        String providerCode,
        String modelCode,
        OffsetDateTime generatedAt,
        String disclaimer) {

    public KnowledgeAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
