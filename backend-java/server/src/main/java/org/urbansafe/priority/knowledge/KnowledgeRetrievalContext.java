package org.urbansafe.priority.knowledge;

import java.util.UUID;

/** PostgreSQL 候选片段召回范围。 */
public record KnowledgeRetrievalContext(
        UUID communityId,
        UUID buildingId,
        int limit) {
}
