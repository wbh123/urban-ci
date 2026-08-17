package org.urbansafe.priority.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.security.AuthenticatedUserContextRunner;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AiIntelligentAnalysisExecutionTaskExecutorTest {

    @Mock
    private SpringAiOrchestrationService orchestrationService;
    @Mock
    private AiExecutionTaskRepository repository;
    @Mock
    private AuthService authService;

    private AiIntelligentAnalysisExecutionTaskExecutor executor;

    @BeforeEach
    void setUp() {
        CurrentUser.clear();
        AiAgentTrace.end();
        executor = new AiIntelligentAnalysisExecutionTaskExecutor(
                orchestrationService,
                repository,
                new AuthenticatedUserContextRunner(authService));
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
        AiAgentTrace.end();
    }

    @Test
    void executeShouldRunToolsWithCurrentAuthenticatedUserContext() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(authService.getCurrentUser(userId)).thenReturn(user(userId, "expert"));
        when(authService.getUserRoles(userId)).thenReturn(List.of("EXPERT"));
        when(orchestrationService.runIntelligentAnalysis(
                eq("BUILDING"), eq(buildingId), eq("综合研判"), anyMap(), eq(userId), eq("expert")))
                .thenAnswer(invocation -> {
                    assertThat(CurrentUser.isAuthenticated()).isTrue();
                    assertThat(CurrentUser.getUserId()).isEqualTo(userId);
                    assertThat(CurrentUser.getRoles()).containsExactly("EXPERT");
                    return new SpringAiOrchestrationService.IntelligentAnalysisResult(
                            executionId,
                            AiAgentExecutionStatus.SUCCEEDED,
                            "ok",
                            List.of(),
                            100L,
                            "deepseek");
                });

        executor.execute(task(taskId, userId, buildingId, null));

        verify(repository).markSucceeded(taskId, executionId);
        assertThat(CurrentUser.isAuthenticated()).isFalse();
    }

    @Test
    void executeShouldBindSourceInferenceForToolsAndClearAfterwards() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID sourceInferenceId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        when(authService.getCurrentUser(userId)).thenReturn(user(userId, "expert"));
        when(authService.getUserRoles(userId)).thenReturn(List.of("EXPERT"));
        when(orchestrationService.runIntelligentAnalysis(
                eq("BUILDING"), eq(buildingId), eq("综合研判"), anyMap(), eq(userId), eq("expert")))
                .thenAnswer(invocation -> {
                    assertThat(AiAgentTrace.contextValue("sourceInferenceId"))
                            .isEqualTo(sourceInferenceId.toString());
                    return new SpringAiOrchestrationService.IntelligentAnalysisResult(
                            executionId,
                            AiAgentExecutionStatus.SUCCEEDED,
                            "ok",
                            List.of(),
                            100L,
                            "deepseek");
                });

        executor.execute(task(taskId, userId, buildingId, sourceInferenceId));

        verify(repository).markSucceeded(taskId, executionId);
        assertThat(AiAgentTrace.contextValue("sourceInferenceId")).isNull();
    }

    @Test
    void executeShouldRejectTaskWhenUserNoLongerHasAnalysisRole() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        when(authService.getCurrentUser(userId)).thenReturn(user(userId, "inspector"));
        when(authService.getUserRoles(userId)).thenReturn(List.of("PROPERTY_INSPECTOR"));

        executor.execute(task(taskId, userId, buildingId, null));

        verify(orchestrationService, never()).runIntelligentAnalysis(
                any(), any(), any(), anyMap(), any(), any());
        verify(repository).markFailed(
                taskId,
                "AI_INTELLIGENT_ANALYSIS_ACCESS_DENIED",
                "AI_INTELLIGENT_ANALYSIS_ACCESS_DENIED");
        assertThat(CurrentUser.isAuthenticated()).isFalse();
    }

    private static AiExecutionTask task(
            UUID taskId, UUID userId, UUID buildingId, UUID sourceInferenceId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("buildingId", buildingId.toString());
        if (sourceInferenceId != null) {
            context.put("sourceInferenceId", sourceInferenceId.toString());
        }
        return new AiExecutionTask(
                taskId,
                null,
                AiIntelligentAnalysisExecutionTaskExecutor.WORKFLOW_CODE,
                "REAL",
                "spring-ai-deepseek",
                "SPRING_AI",
                "TEXT_GENERATION",
                "综合研判",
                "test:" + taskId,
                userId,
                Map.of(
                        "businessType", "BUILDING",
                        "businessId", buildingId.toString(),
                        "question", "综合研判",
                        "context", context,
                        "taskType", "INTELLIGENT_ANALYSIS"),
                "RUNNING",
                1,
                2,
                OffsetDateTime.now(),
                "worker-test",
                OffsetDateTime.now().plusMinutes(1),
                null,
                null,
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now());
    }

    private static CurrentUserResult user(UUID userId, String username) {
        return new CurrentUserResult(
                userId,
                username,
                username,
                null,
                null,
                null,
                "ACTIVE",
                List.of(),
                OffsetDateTime.now());
    }
}
