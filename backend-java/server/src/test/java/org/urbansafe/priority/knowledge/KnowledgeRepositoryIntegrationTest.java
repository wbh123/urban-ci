package org.urbansafe.priority.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class KnowledgeRepositoryIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private KnowledgeRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateScopedDocumentRetrieveCandidateAndPersistCitation() {
        UUID buildingId = UUID.randomUUID();
        KnowledgeDocument document = repository.createDocument(new KnowledgeDocumentCommand(
                "PHOTO_GUIDE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "巡检图片拍摄规范", "GUIDE", "1.0", "INTERNAL", Set.of("EXPERT"),
                null, buildingId, "ACTIVE", "minio://knowledge/photo-guide.pdf",
                "b".repeat(64), null, null, Map.of("source", "test"),
                List.of(new KnowledgeChunkDraft(
                        0, "模糊图片补拍", 5,
                        "巡检图片模糊时，应补拍整体照和带比例尺的近景细节照。",
                        Map.of("reviewed", true))),
                UUID.randomUUID()));

        List<KnowledgeCandidate> candidates = repository.findCandidates(
                new KnowledgeRetrievalContext(null, buildingId, 500));
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.documentId()).isEqualTo(document.documentId());
            assertThat(candidate.content()).contains("比例尺");
            assertThat(candidate.roleScope()).containsExactly("EXPERT");
        });

        UUID requester = UUID.randomUUID();
        UUID questionId = repository.createQuestion(new KnowledgeQuestionLog(
                "如何补拍模糊图片？", null, buildingId, requester, List.of("EXPERT")));
        KnowledgeCandidate candidate = candidates.stream()
                .filter(item -> item.documentId().equals(document.documentId()))
                .findFirst().orElseThrow();
        KnowledgeCitation citation = new KnowledgeCitation(
                UUID.randomUUID(), candidate.documentId(), candidate.documentCode(),
                candidate.documentTitle(), candidate.documentVersion(), candidate.chunkId(),
                candidate.sectionTitle(), candidate.pageNumber(), candidate.content(), 1, 0.9d);
        repository.complete(new KnowledgeAnswer(
                questionId, "ANSWERED", "根据知识库，应补拍带比例尺的近景照。", true,
                List.of(citation), "SPRING_BOOT", "LOCAL-RAG-EXTRACTIVE-001",
                java.time.OffsetDateTime.now(), KnowledgeQaService.DISCLAIMER));

        Integer citations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge.citation WHERE question_id=?",
                Integer.class, questionId);
        assertThat(citations).isEqualTo(1);
    }
}
