package org.urbansafe.priority.knowledge;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;
import org.urbansafe.priority.common.exception.InvalidRequestException;

/** 受权限控制、强制引用且证据不足时拒答的内部知识问答服务。 */
@Service
public class KnowledgeQaService {

    public static final String DISCLAIMER =
            "本答案仅用于内部巡检与业务操作辅助，不构成房屋安全鉴定、风险等级或行政处置结论。";
    public static final String FALLBACK_PROVIDER_CODE = "SPRING_BOOT";
    public static final String FALLBACK_MODEL_CODE = "LOCAL-RAG-EXTRACTIVE-001";
    private static final Logger log = LoggerFactory.getLogger(KnowledgeQaService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "PROPERTY_INSPECTOR", "EXPERT");
    private static final double EVIDENCE_THRESHOLD = 0.28d;
    private static final int MAX_CONTEXT_CHARACTERS = 2400;
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final KnowledgeRepository repository;
    private final KnowledgeTextScorer scorer;
    private final Optional<KnowledgeAnswerGenerator> answerGenerator;
    private final Optional<AiAutomationSettingsService> automationSettingsService;

    public KnowledgeQaService(KnowledgeRepository repository, KnowledgeTextScorer scorer) {
        this(repository, scorer, Optional.empty(), Optional.empty());
    }

    public KnowledgeQaService(
            KnowledgeRepository repository,
            KnowledgeTextScorer scorer,
            Optional<KnowledgeAnswerGenerator> answerGenerator) {
        this(repository, scorer, answerGenerator, Optional.empty());
    }

    @Autowired
    public KnowledgeQaService(
            KnowledgeRepository repository,
            KnowledgeTextScorer scorer,
            Optional<KnowledgeAnswerGenerator> answerGenerator,
            Optional<AiAutomationSettingsService> automationSettingsService) {
        this.repository = repository;
        this.scorer = scorer;
        this.answerGenerator = answerGenerator == null ? Optional.empty() : answerGenerator;
        this.automationSettingsService = automationSettingsService == null
                ? Optional.empty()
                : automationSettingsService;
    }

    public KnowledgeDocument createDocument(KnowledgeDocumentCommand command) {
        validateDocument(command);
        return repository.createDocument(command);
    }

    public KnowledgeAnswer ask(KnowledgeQuestionCommand command) {
        if (automationSettingsService.isPresent()
                && !automationSettingsService.get().knowledgeQaEnabled()) {
            throw new InvalidRequestException(
                    "KNOWLEDGE_QA_DISABLED",
                    "知识问答已由系统管理员关闭");
        }
        validateQuestion(command);
        UUID questionId = repository.createQuestion(new KnowledgeQuestionLog(
                command.question(), command.communityId(), command.buildingId(),
                command.requestedBy(), command.roles()));
        try {
            Set<String> currentRoles = normalizeRoles(command.roles());
            List<ScoredCandidate> ranked = repository.findCandidates(
                            new KnowledgeRetrievalContext(command.communityId(), command.buildingId(), 500))
                    .stream()
                    .filter(candidate -> authorized(candidate, currentRoles))
                    .map(candidate -> new ScoredCandidate(candidate,
                            scorer.score(command.question(), candidate.sectionTitle(), candidate.content())))
                    .filter(candidate -> candidate.score() >= EVIDENCE_THRESHOLD)
                    .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                            .thenComparing(candidate -> candidate.candidate().documentCode())
                            .thenComparing(candidate -> candidate.candidate().chunkId()))
                    .limit(command.topK())
                    .toList();
            KnowledgeAnswer answer = ranked.isEmpty()
                    ? refused(questionId)
                    : answered(questionId, command.question(), ranked);
            repository.complete(answer);
            return answer;
        } catch (RuntimeException ex) {
            repository.failQuestion(questionId, "KNOWLEDGE_QA_FAILED", safeMessage(ex));
            throw ex;
        }
    }

    private static boolean authorized(KnowledgeCandidate candidate, Set<String> currentRoles) {
        if ("PUBLIC".equalsIgnoreCase(candidate.securityLevel())) {
            return true;
        }
        return candidate.roleScope().stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(currentRoles::contains);
    }

    private KnowledgeAnswer answered(UUID questionId, String question, List<ScoredCandidate> ranked) {
        List<KnowledgeCitation> citations = new ArrayList<>();
        StringBuilder fallback = new StringBuilder("根据当前已审核知识库：");
        int rank = 1;
        int usedCharacters = 0;
        for (ScoredCandidate scored : ranked) {
            KnowledgeCandidate candidate = scored.candidate();
            String excerpt = sanitizeEvidence(candidate.content());
            if (excerpt.isBlank() || usedCharacters >= MAX_CONTEXT_CHARACTERS) {
                continue;
            }
            excerpt = truncate(excerpt, Math.min(700, MAX_CONTEXT_CHARACTERS - usedCharacters));
            usedCharacters += excerpt.length();
            citations.add(new KnowledgeCitation(
                    UUID.randomUUID(), candidate.documentId(), candidate.documentCode(),
                    candidate.documentTitle(), candidate.documentVersion(), candidate.chunkId(),
                    candidate.sectionTitle(), candidate.pageNumber(), excerpt, rank, scored.score()));
            fallback.append("\n").append(rank).append(". ").append(excerpt)
                    .append("（来源：《").append(candidate.documentTitle()).append("》")
                    .append("，版本 ").append(candidate.documentVersion());
            if (candidate.sectionTitle() != null && !candidate.sectionTitle().isBlank()) {
                fallback.append("，章节“").append(candidate.sectionTitle()).append("”");
            }
            if (candidate.pageNumber() != null) {
                fallback.append("，第 ").append(candidate.pageNumber()).append(" 页");
            }
            fallback.append("）");
            rank++;
        }
        if (citations.isEmpty()) {
            return refused(questionId);
        }

        Optional<KnowledgeAnswerGenerator.GeneratedAnswer> generated = generateWithDeepSeek(question, citations);
        if (generated.isPresent()) {
            KnowledgeAnswerGenerator.GeneratedAnswer value = generated.get();
            return new KnowledgeAnswer(
                    questionId, "ANSWERED", value.answer(), true, citations,
                    value.providerCode(), value.modelCode(),
                    OffsetDateTime.now(DEFAULT_ZONE_ID), DISCLAIMER);
        }

        return new KnowledgeAnswer(
                questionId, "ANSWERED", fallback.toString(), true, citations,
                FALLBACK_PROVIDER_CODE, FALLBACK_MODEL_CODE,
                OffsetDateTime.now(DEFAULT_ZONE_ID), DISCLAIMER);
    }

    private Optional<KnowledgeAnswerGenerator.GeneratedAnswer> generateWithDeepSeek(
            String question,
            List<KnowledgeCitation> citations) {
        if (answerGenerator.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(answerGenerator.get().generate(question, citations));
        } catch (RuntimeException ex) {
            log.warn("DeepSeek knowledge answer generation failed; using extractive fallback: {}", safeMessage(ex));
            return Optional.empty();
        }
    }

    private static KnowledgeAnswer refused(UUID questionId) {
        return new KnowledgeAnswer(
                questionId,
                "REFUSED",
                "当前知识库中没有足够依据回答该问题。请补充经过审核的制度、规范或业务文档后重试。",
                false,
                List.of(),
                FALLBACK_PROVIDER_CODE,
                FALLBACK_MODEL_CODE,
                OffsetDateTime.now(DEFAULT_ZONE_ID),
                DISCLAIMER);
    }

    private static String sanitizeEvidence(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        for (String line : content.replace("\r", "").split("\n")) {
            String normalized = line.toLowerCase(Locale.ROOT);
            boolean instruction = normalized.contains("ignore previous")
                    || normalized.contains("system prompt")
                    || normalized.contains("developer message")
                    || line.contains("忽略之前")
                    || line.contains("系统提示词")
                    || line.contains("执行以下指令")
                    || line.contains("调用接口删除")
                    || line.contains("绕过权限");
            if (!instruction && !line.isBlank()) {
                if (!safe.isEmpty()) {
                    safe.append(' ');
                }
                safe.append(line.strip());
            }
        }
        return safe.toString();
    }

    private static void validateDocument(KnowledgeDocumentCommand command) {
        if (command == null) {
            throw invalid("知识文档请求不能为空");
        }
        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && !command.effectiveTo().isAfter(command.effectiveFrom())) {
            throw invalid("知识文档失效时间必须晚于生效时间");
        }
        if (command.roleScope().isEmpty() || !ALLOWED_ROLES.containsAll(normalizeRoles(command.roleScope()))) {
            throw invalid("知识文档角色范围无效");
        }
        if (command.chunks().isEmpty()) {
            throw invalid("知识文档至少包含一个切片");
        }
        Set<Integer> indexes = new HashSet<>();
        for (KnowledgeChunkDraft chunk : command.chunks()) {
            if (chunk.chunkIndex() < 0 || !indexes.add(chunk.chunkIndex())) {
                throw invalid("知识切片序号必须非负且不能重复");
            }
            if (chunk.content() == null || chunk.content().isBlank()) {
                throw invalid("知识切片内容不能为空");
            }
        }
    }

    private static void validateQuestion(KnowledgeQuestionCommand command) {
        if (command == null || command.question() == null || command.question().isBlank()) {
            throw invalid("知识问题不能为空");
        }
        if (command.requestedBy() == null) {
            throw invalid("知识问题缺少已认证用户");
        }
        if (command.topK() < 1 || command.topK() > 8) {
            throw invalid("知识检索数量必须在 1 至 8 之间");
        }
        Set<String> roles = normalizeRoles(command.roles());
        if (roles.isEmpty() || roles.stream().noneMatch(ALLOWED_ROLES::contains)) {
            throw invalid("当前角色无权使用内部知识问答");
        }
    }

    private static Set<String> normalizeRoles(Iterable<String> roles) {
        Set<String> result = new HashSet<>();
        if (roles == null) {
            return result;
        }
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                result.add(role.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", ""));
            }
        }
        return result;
    }

    private static InvalidRequestException invalid(String message) {
        return new InvalidRequestException("KNOWLEDGE_REQUEST_INVALID", message);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "知识问答执行失败" : truncate(message, 1000);
    }

    private record ScoredCandidate(KnowledgeCandidate candidate, double score) {
    }
}
