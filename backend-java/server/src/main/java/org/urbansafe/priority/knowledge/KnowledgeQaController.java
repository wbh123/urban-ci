package org.urbansafe.priority.knowledge;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.KnowledgeQaApi;
import org.urbansafe.priority.model.dto.KnowledgeAnswerSuccessResponse;
import org.urbansafe.priority.model.dto.KnowledgeAnswerView;
import org.urbansafe.priority.model.dto.KnowledgeChunkCreateRequest;
import org.urbansafe.priority.model.dto.KnowledgeCitationView;
import org.urbansafe.priority.model.dto.KnowledgeDocumentCreateRequest;
import org.urbansafe.priority.model.dto.KnowledgeDocumentSuccessResponse;
import org.urbansafe.priority.model.dto.KnowledgeDocumentView;
import org.urbansafe.priority.model.dto.KnowledgeQuestionRequest;

/** 内部知识文档登记和可引用问答接口。 */
@RestController
public class KnowledgeQaController implements KnowledgeQaApi {

    private final KnowledgeQaService service;

    public KnowledgeQaController(KnowledgeQaService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KnowledgeDocumentSuccessResponse> createKnowledgeDocument(
            KnowledgeDocumentCreateRequest request) {
        KnowledgeDocument document = service.createDocument(toCommand(request));
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        KnowledgeDocumentSuccessResponse response = new KnowledgeDocumentSuccessResponse();
        response.setSuccess(metadata.success());
        response.setData(toDto(document));
        response.setError(null);
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        return ResponseEntity.created(URI.create(
                        "/api/v1/knowledge/documents/" + document.documentId()))
                .body(response);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','PROPERTY_INSPECTOR','EXPERT')")
    public ResponseEntity<KnowledgeAnswerSuccessResponse> askKnowledgeQuestion(
            KnowledgeQuestionRequest request) {
        int topK = request.getTopK() == null ? 5 : request.getTopK();
        KnowledgeAnswer answer = service.ask(new KnowledgeQuestionCommand(
                request.getQuestion(), request.getCommunityId(), request.getBuildingId(), topK,
                CurrentUser.getUserId(), CurrentUser.getRoles()));
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        KnowledgeAnswerSuccessResponse response = new KnowledgeAnswerSuccessResponse();
        response.setSuccess(metadata.success());
        response.setData(toDto(answer));
        response.setError(null);
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        return ResponseEntity.ok(response);
    }

    private static KnowledgeDocumentCommand toCommand(KnowledgeDocumentCreateRequest request) {
        Set<String> roleScope = request.getRoleScope().stream()
                .map(KnowledgeDocumentCreateRequest.RoleScopeEnum::getValue)
                .collect(Collectors.toUnmodifiableSet());
        List<KnowledgeChunkDraft> chunks = request.getChunks().stream()
                .map(KnowledgeQaController::toChunk)
                .toList();
        return new KnowledgeDocumentCommand(
                request.getDocumentCode(), request.getTitle(), request.getDocumentType(),
                request.getDocumentVersion(), request.getSecurityLevel().getValue(), roleScope,
                request.getCommunityId(), request.getBuildingId(), request.getStatus().getValue(),
                request.getSourceUri(), request.getContentChecksum(), request.getEffectiveFrom(),
                request.getEffectiveTo(), metadata(request.getMetadata()), chunks,
                CurrentUser.getUserId());
    }

    private static KnowledgeChunkDraft toChunk(KnowledgeChunkCreateRequest request) {
        return new KnowledgeChunkDraft(
                request.getChunkIndex(), request.getSectionTitle(), request.getPageNumber(),
                request.getContent(), metadata(request.getMetadata()));
    }

    private static Map<String, Object> metadata(Map<String, Object> metadata) {
        return metadata == null ? Map.of() : metadata;
    }

    private static KnowledgeDocumentView toDto(KnowledgeDocument document) {
        KnowledgeDocumentView dto = new KnowledgeDocumentView();
        dto.setDocumentId(document.documentId());
        dto.setDocumentCode(document.documentCode());
        dto.setTitle(document.title());
        dto.setDocumentVersion(document.documentVersion());
        dto.setStatus(document.status());
        dto.setChunkCount(document.chunkCount());
        dto.setCreatedAt(document.createdAt());
        return dto;
    }

    private static KnowledgeAnswerView toDto(KnowledgeAnswer answer) {
        KnowledgeAnswerView dto = new KnowledgeAnswerView();
        dto.setQuestionId(answer.questionId());
        dto.setStatus(KnowledgeAnswerView.StatusEnum.fromValue(answer.status()));
        dto.setAnswer(answer.answer());
        dto.setEvidenceSufficient(answer.evidenceSufficient());
        dto.setCitations(answer.citations().stream().map(KnowledgeQaController::toDto).toList());
        dto.setProviderCode(answer.providerCode());
        dto.setModelCode(answer.modelCode());
        dto.setGeneratedAt(answer.generatedAt());
        dto.setDisclaimer(answer.disclaimer());
        return dto;
    }

    private static KnowledgeCitationView toDto(KnowledgeCitation citation) {
        KnowledgeCitationView dto = new KnowledgeCitationView();
        dto.setCitationId(citation.citationId());
        dto.setDocumentId(citation.documentId());
        dto.setDocumentCode(citation.documentCode());
        dto.setDocumentTitle(citation.documentTitle());
        dto.setDocumentVersion(citation.documentVersion());
        dto.setChunkId(citation.chunkId());
        dto.setSectionTitle(citation.sectionTitle());
        dto.setPageNumber(citation.pageNumber());
        dto.setExcerpt(citation.excerpt());
        dto.setRank(citation.rank());
        dto.setScore(citation.score());
        return dto;
    }
}
