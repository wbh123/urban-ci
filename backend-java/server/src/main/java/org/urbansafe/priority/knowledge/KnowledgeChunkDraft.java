package org.urbansafe.priority.knowledge;

import java.util.Map;

/** 管理员登记知识文档时提交的受控切片。 */
public record KnowledgeChunkDraft(
        int chunkIndex,
        String sectionTitle,
        Integer pageNumber,
        String content,
        Map<String, Object> metadata) {

    public KnowledgeChunkDraft {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
