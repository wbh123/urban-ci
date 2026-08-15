package org.urbansafe.priority.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.urbansafe.priority.ai.config.SpringAiProviderProperties;
import org.urbansafe.priority.ai.execution.AiAgentExecutionRepository;
import org.urbansafe.priority.ai.execution.AiAgentExecutionStatus;
import org.urbansafe.priority.ai.execution.AiAgentExecutionStep;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentStepType;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.tools.BuildingOverviewTool;
import org.urbansafe.priority.ai.tools.DifyReportDraftTool;
import org.urbansafe.priority.ai.tools.DifyReviewAssistTool;
import org.urbansafe.priority.ai.tools.InspectionEvidenceTool;
import org.urbansafe.priority.ai.tools.KnowledgeRetrievalTool;
import org.urbansafe.priority.ai.tools.LatestVisionAnalysisTool;
import org.urbansafe.priority.ai.tools.RenewalPriorityTool;
import org.urbansafe.priority.ai.tools.RiskAssessmentTool;
import org.urbansafe.priority.ai.tools.VisionAnalysisTool;

/** Spring AI 智能编排服务测试（Mock ChatModel，不消耗真实 DeepSeek API）。 */
class SpringAiOrchestrationServiceTest {

    private SpringAiProviderProperties configuredProperties() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.deepseek.com");
        properties.setModel("deepseek-v4-flash");
        properties.setChatModel("openai");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private SpringAiOrchestrationService buildService(
            SpringAiProviderProperties properties,
            ChatClient.ChatClientRequestSpec promptSpec,
            ChatClient.CallResponseSpec callSpec) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient.Builder cloned = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        when(builder.clone()).thenReturn(cloned);
        when(cloned.defaultTools(any(Object[].class))).thenReturn(cloned);
        when(cloned.build()).thenReturn(client);
        when(client.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);

        ObjectProvider<ChatClient.Builder> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(builder);
        when(provider.getIfAvailable()).thenReturn(builder);

        AiAutomationSettingsService automation = mock(AiAutomationSettingsService.class);
        when(automation.intelligentWorkflowEnabled()).thenReturn(true);
        when(automation.knowledgeQaEnabled()).thenReturn(true);

        AiAgentExecutionRepository repository = mock(AiAgentExecutionRepository.class);
        return new SpringAiOrchestrationService(
                provider,
                properties,
                repository,
                automation,
                mock(VisionAnalysisTool.class),
                mock(BuildingOverviewTool.class),
                mock(RiskAssessmentTool.class),
                mock(RenewalPriorityTool.class),
                mock(InspectionEvidenceTool.class),
                mock(LatestVisionAnalysisTool.class),
                mock(DifyReviewAssistTool.class),
                mock(DifyReportDraftTool.class),
                mock(KnowledgeRetrievalTool.class));
    }

    @Test
    void degradesWhenDeepSeekNotConfiguredInsteadOfThrowingServerError() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();
        SpringAiOrchestrationService service = buildService(properties, mock(), mock());

        SpringAiOrchestrationService.IntelligentAnalysisResult result = service.runIntelligentAnalysis(
                "AI_INFERENCE",
                UUID.randomUUID(),
                "问题",
                Map.of("assetId", UUID.randomUUID().toString()),
                UUID.randomUUID(),
                "t");

        assertThat(result.status()).isEqualTo(AiAgentExecutionStatus.PARTIAL_SUCCEEDED);
        assertThat(result.answer()).contains("智能文本能力暂不可用");
        assertThat(result.answer()).contains("人工复核");
    }

    @Test
    void succeedsWhenLlmReturnsAnswer() {
        SpringAiProviderProperties properties = configuredProperties();
        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(callSpec.content()).thenReturn("该楼栋存在疑似裂缝，建议优先复核。");
        SpringAiOrchestrationService service = buildService(properties, promptSpec, callSpec);

        SpringAiOrchestrationService.IntelligentAnalysisResult result = service.runIntelligentAnalysis(
                "BUILDING", UUID.randomUUID(), "请综合分析", Map.of(), UUID.randomUUID(), "t");

        assertThat(result.status()).isEqualTo(AiAgentExecutionStatus.SUCCEEDED);
        assertThat(result.answer()).contains("疑似裂缝");
        assertThat(result.steps()).anyMatch(step -> "LLM".equals(step.type()));
    }

    @Test
    void failsWhenLlmThrowsAndReturnsToolSummaryFallback() {
        SpringAiProviderProperties properties = configuredProperties();
        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(callSpec.content()).thenThrow(new RuntimeException("deepseek boom"));
        SpringAiOrchestrationService service = buildService(properties, promptSpec, callSpec);

        SpringAiOrchestrationService.IntelligentAnalysisResult result = service.runIntelligentAnalysis(
                "BUILDING", UUID.randomUUID(), "请综合分析", Map.of(), UUID.randomUUID(), "t");

        assertThat(result.status()).isEqualTo(AiAgentExecutionStatus.FAILED);
        assertThat(result.answer()).contains("DeepSeek 智能编排当前不可用");
    }

    @Test
    void resolveFinalStatusPartialWhenToolStepFailed() {
        AiAgentExecutionStep failedTool = new AiAgentExecutionStep(
                1, AiAgentStepType.TOOL, "DifyReviewAssistTool", "DIFY",
                AiAgentStepStatus.FAILED, 10L, "AI_PROVIDER_DISABLED", "不可用", null);
        assertThat(SpringAiOrchestrationService.resolveFinalStatus(List.of(failedTool)))
                .isEqualTo(AiAgentExecutionStatus.PARTIAL_SUCCEEDED);

        AiAgentExecutionStep okTool = new AiAgentExecutionStep(
                1, AiAgentStepType.TOOL, "BuildingOverviewTool", "SPRING_BOOT",
                AiAgentStepStatus.SUCCEEDED, 5L, null, null, null);
        assertThat(SpringAiOrchestrationService.resolveFinalStatus(List.of(okTool)))
                .isEqualTo(AiAgentExecutionStatus.SUCCEEDED);
    }

    @Test
    void getExecutionNotFoundThrows() {
        SpringAiOrchestrationService service = buildService(configuredProperties(), mock(), mock());
        assertThatThrownBy(() -> service.getExecution(UUID.randomUUID()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("执行记录不存在");
    }
}
