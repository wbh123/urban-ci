package org.urbansafe.priority.knowledge;

import java.util.Set;
import java.util.UUID;

/** 经状态和业务范围过滤后等待权限校验及重排的知识片段。 */
public record KnowledgeCandidate(
        UUID chunkId,
        UUID documentId,
        String documentCode,
        String documentTitle,
        String documentVersion,
        String securityLevel,
        Set<String> roleScope,
        UUID communityId,
        UUID buildingId,
        String sectionTitle,
        Integer pageNumber,
        String content) {

    public KnowledgeCandidate {
        roleScope = roleScope == null ? Set.of() : Set.copyOf(roleScope);
    }
}
