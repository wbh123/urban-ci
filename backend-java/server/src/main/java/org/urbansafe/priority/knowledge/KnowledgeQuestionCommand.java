package org.urbansafe.priority.knowledge;

import java.util.List;
import java.util.UUID;

/** 内部知识问题和当前权限上下文。 */
public record KnowledgeQuestionCommand(
        String question,
        UUID communityId,
        UUID buildingId,
        int topK,
        UUID requestedBy,
        List<String> roles) {

    public KnowledgeQuestionCommand {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
