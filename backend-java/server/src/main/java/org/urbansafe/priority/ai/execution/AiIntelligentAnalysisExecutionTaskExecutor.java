package org.urbansafe.priority.ai.execution;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.auth.security.AuthenticatedUserContextRunner;
import org.urbansafe.priority.auth.security.CurrentUser;

/** 执行持久化的 Spring AI 智能综合研判任务。 */
@Component
public class AiIntelligentAnalysisExecutionTaskExecutor {

    public static final String WORKFLOW_CODE = "SPRING-AI-INTELLIGENT-ANALYSIS-001";
    private static final Set<String> ANALYSIS_ROLES = Set.of(
            "ADMIN", "EXPERT", "PROFESSIONAL_REVIEWER");

    private final SpringAiOrchestrationService orchestrationService;
    private final AiExecutionTaskRepository repository;
    private final AuthenticatedUserContextRunner authenticatedUserContextRunner;

    public AiIntelligentAnalysisExecutionTaskExecutor(
            SpringAiOrchestrationService orchestrationService,
            AiExecutionTaskRepository repository,
            AuthenticatedUserContextRunner authenticatedUserContextRunner) {
        this.orchestrationService = orchestrationService;
        this.repository = repository;
        this.authenticatedUserContextRunner = authenticatedUserContextRunner;
    }

    public void execute(AiExecutionTask task) {
        try {
            Map<String, Object> inputs = task.inputs();
            String businessType = string(inputs.get("businessType"));
            UUID businessId = uuid(inputs.get("businessId"));
            String question = string(inputs.get("question"));
            Map<String, Object> context = context(inputs.get("context"));

            SpringAiOrchestrationService.IntelligentAnalysisResult result =
                    authenticatedUserContextRunner.runAs(task.requestedBy(), () -> {
                        assertCurrentUserCanRunAnalysis();
                        AiAgentTrace.bindContext(context);
                        try {
                            return orchestrationService.runIntelligentAnalysis(
                                    businessType,
                                    businessId,
                                    question,
                                    context,
                                    task.requestedBy(),
                                    CurrentUser.getUsername());
                        } finally {
                            AiAgentTrace.clearContext();
                        }
                    });

            repository.markSucceeded(task.id(), result.executionId());
        } catch (AccessDeniedException ex) {
            repository.markFailed(
                    task.id(),
                    "AI_INTELLIGENT_ANALYSIS_ACCESS_DENIED",
                    safeMessage(ex));
        } catch (RuntimeException ex) {
            repository.markFailed(task.id(), "AI_INTELLIGENT_ANALYSIS_FAILED", safeMessage(ex));
        }
    }

    private static void assertCurrentUserCanRunAnalysis() {
        boolean allowed = CurrentUser.getRoles().stream().anyMatch(ANALYSIS_ROLES::contains);
        if (!allowed) {
            throw new AccessDeniedException("AI_INTELLIGENT_ANALYSIS_ACCESS_DENIED");
        }
    }

    private static Map<String, Object> context(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String string(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
    }
}
