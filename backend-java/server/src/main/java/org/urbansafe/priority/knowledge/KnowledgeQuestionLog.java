package org.urbansafe.priority.knowledge;

import java.util.List;
import java.util.UUID;

/** 写入问答审计表的最小请求上下文。 */
public record KnowledgeQuestionLog(
        String question,
        UUID communityId,
        UUID buildingId,
        UUID requestedBy,
        List<String> roles) {

    public KnowledgeQuestionLog {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
