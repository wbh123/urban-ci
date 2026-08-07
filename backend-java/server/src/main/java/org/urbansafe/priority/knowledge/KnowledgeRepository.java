package org.urbansafe.priority.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.common.exception.ResourceConflictException;

/** PostgreSQL 知识文档、候选片段、问题和引用仓储。 */
@Repository
public class KnowledgeRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public KnowledgeRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeDocument createDocument(KnowledgeDocumentCommand command) {
        UUID documentId = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("documentCode", command.documentCode())
                .addValue("title", command.title())
                .addValue("documentType", command.documentType())
                .addValue("documentVersion", command.documentVersion())
                .addValue("status", command.status())
                .addValue("securityLevel", command.securityLevel())
                .addValue("roleScope", String.join(",", command.roleScope()))
                .addValue("communityId", command.communityId(), Types.OTHER)
                .addValue("buildingId", command.buildingId(), Types.OTHER)
                .addValue("sourceUri", command.sourceUri())
                .addValue("checksum", command.contentChecksum())
                .addValue("effectiveFrom", command.effectiveFrom())
                .addValue("effectiveTo", command.effectiveTo())
                .addValue("metadata", json(command.metadata()))
                .addValue("createdBy", command.createdBy(), Types.OTHER)
                .addValue("createdAt", createdAt);
        try {
            jdbc.update("""
                    INSERT INTO knowledge.document
                        (id, document_code, title, document_type, document_version, status,
                         security_level, role_scope, community_id, building_id, source_uri,
                         content_checksum, effective_from, effective_to, metadata, created_by,
                         created_at, updated_at)
                    VALUES
                        (:id, :documentCode, :title, :documentType, :documentVersion, :status,
                         :securityLevel, string_to_array(:roleScope, ','), :communityId, :buildingId,
                         :sourceUri, :checksum, :effectiveFrom, :effectiveTo, CAST(:metadata AS jsonb),
                         :createdBy, :createdAt, :createdAt)
                    """, params);
        } catch (DuplicateKeyException ex) {
            throw new ResourceConflictException(
                    "KNOWLEDGE_DOCUMENT_CONFLICT", "知识文档编号和版本已经存在");
        }
        for (KnowledgeChunkDraft chunk : command.chunks()) {
            jdbc.update("""
                    INSERT INTO knowledge.chunk
                        (id, document_id, chunk_index, section_title, page_number, content, metadata)
                    VALUES
                        (:id, :documentId, :chunkIndex, :sectionTitle, :pageNumber,
                         :content, CAST(:metadata AS jsonb))
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("documentId", documentId)
                    .addValue("chunkIndex", chunk.chunkIndex())
                    .addValue("sectionTitle", chunk.sectionTitle())
                    .addValue("pageNumber", chunk.pageNumber())
                    .addValue("content", chunk.content())
                    .addValue("metadata", json(chunk.metadata())));
        }
        return new KnowledgeDocument(
                documentId, command.documentCode(), command.title(), command.documentVersion(),
                command.status(), command.chunks().size(), createdAt);
    }

    public List<KnowledgeCandidate> findCandidates(KnowledgeRetrievalContext context) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS chunk_id, d.id AS document_id, d.document_code,
                       d.title AS document_title, d.document_version, d.security_level,
                       d.role_scope, d.community_id, d.building_id, c.section_title,
                       c.page_number, c.content
                FROM knowledge.document d
                JOIN knowledge.chunk c ON c.document_id=d.id
                WHERE d.status='ACTIVE'
                  AND (d.effective_from IS NULL OR d.effective_from <= CURRENT_TIMESTAMP)
                  AND (d.effective_to IS NULL OR d.effective_to > CURRENT_TIMESTAMP)
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (context.communityId() == null) {
            sql.append(" AND d.community_id IS NULL");
        } else {
            sql.append(" AND (d.community_id IS NULL OR d.community_id=:communityId)");
            params.addValue("communityId", context.communityId(), Types.OTHER);
        }
        if (context.buildingId() == null) {
            sql.append(" AND d.building_id IS NULL");
        } else {
            sql.append(" AND (d.building_id IS NULL OR d.building_id=:buildingId)");
            params.addValue("buildingId", context.buildingId(), Types.OTHER);
        }
        sql.append(" ORDER BY d.updated_at DESC, c.chunk_index LIMIT :limit");
        params.addValue("limit", Math.max(1, Math.min(context.limit(), 500)));
        return jdbc.query(sql.toString(), params, (rs, rowNum) -> candidate(rs));
    }

    public UUID createQuestion(KnowledgeQuestionLog question) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO knowledge.question
                    (id, question_text, requested_by, request_context, status)
                VALUES
                    (:id, :question, :requestedBy, CAST(:context AS jsonb), 'PENDING')
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("question", question.question())
                .addValue("requestedBy", question.requestedBy(), Types.OTHER)
                .addValue("context", json(Map.of(
                        "communityId", string(question.communityId()),
                        "buildingId", string(question.buildingId()),
                        "roles", question.roles()))));
        return id;
    }

    @Transactional
    public void complete(KnowledgeAnswer answer) {
        jdbc.update("""
                UPDATE knowledge.question
                SET answer_text=:answer,
                    evidence_sufficient=:evidenceSufficient,
                    workflow_code='LOCAL-KNOWLEDGE-RETRIEVAL-001',
                    workflow_version='1.0.0',
                    model_code=:modelCode,
                    provider_code=:providerCode,
                    status=:status,
                    error_code=NULL,
                    error_message=NULL,
                    answered_at=:answeredAt
                WHERE id=:questionId
                """, new MapSqlParameterSource()
                .addValue("questionId", answer.questionId())
                .addValue("answer", answer.answer())
                .addValue("evidenceSufficient", answer.evidenceSufficient())
                .addValue("modelCode", answer.modelCode())
                .addValue("providerCode", answer.providerCode())
                .addValue("status", answer.status())
                .addValue("answeredAt", answer.generatedAt()));
        for (KnowledgeCitation citation : answer.citations()) {
            jdbc.update("""
                    INSERT INTO knowledge.citation
                        (id, question_id, chunk_id, citation_order, relevance_score, quoted_text)
                    VALUES
                        (:id, :questionId, :chunkId, :rank, :score, :excerpt)
                    """, new MapSqlParameterSource()
                    .addValue("id", citation.citationId())
                    .addValue("questionId", answer.questionId())
                    .addValue("chunkId", citation.chunkId())
                    .addValue("rank", citation.rank())
                    .addValue("score", citation.score())
                    .addValue("excerpt", citation.excerpt()));
        }
    }

    public void failQuestion(UUID questionId, String errorCode, String errorMessage) {
        jdbc.update("""
                UPDATE knowledge.question
                SET status='FAILED', error_code=:errorCode, error_message=:errorMessage,
                    answered_at=CURRENT_TIMESTAMP
                WHERE id=:questionId
                """, new MapSqlParameterSource()
                .addValue("questionId", questionId)
                .addValue("errorCode", errorCode)
                .addValue("errorMessage", truncate(errorMessage, 1000)));
    }

    private KnowledgeCandidate candidate(ResultSet rs) throws SQLException {
        return new KnowledgeCandidate(
                rs.getObject("chunk_id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("document_code"),
                rs.getString("document_title"),
                rs.getString("document_version"),
                rs.getString("security_level"),
                roles(rs.getArray("role_scope")),
                rs.getObject("community_id", UUID.class),
                rs.getObject("building_id", UUID.class),
                rs.getString("section_title"),
                (Integer) rs.getObject("page_number"),
                rs.getString("content"));
    }

    private static Set<String> roles(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        Object raw = array.getArray();
        if (raw instanceof String[] values) {
            return new LinkedHashSet<>(Arrays.asList(values));
        }
        if (raw instanceof Object[] values) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Object value : values) {
                result.add(String.valueOf(value));
            }
            return result;
        }
        return Set.of();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("知识问答上下文无法序列化", ex);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
