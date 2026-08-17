package org.urbansafe.priority.ai.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
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
    void intelligentWorkflowExposesReviewAssistButNotUnfinishedReportDraft() {
        SpringAiOrchestrationService service = buildService(configuredProperties(), mock(), mock());

        Object[] tools = ReflectionTestUtils.invokeMethod(service, "selectTools", "BUILDING", Map.of());

        assertThat(tools).isNotNull();
        assertThat(tools).anyMatch(DifyReviewAssistTool.class::isInstance);
        assertThat(tools).noneMatch(DifyReportDraftTool.class::isInstance);
    }

    @Test
    void automaticAnalysisWithSourceInferenceDoesNotExposeVisionAnalysisTool() {
        SpringAiOrchestrationService service = buildService(configuredProperties(), mock(), mock());

        Object[] tools = ReflectionTestUtils.invokeMethod(service, "selectTools", "AI_INFERENCE",
                Map.of(
                        "assetId", UUID.randomUUID().toString(),
                        "sourceInferenceId", UUID.randomUUID().toString()));

        assertThat(tools).noneMatch(VisionAnalysisTool.class::isInstance);
        assertThat(tools).anyMatch(LatestVisionAnalysisTool.class::isInstance);
        assertThat(tools).anyMatch(DifyReviewAssistTool.class::isInstance);
    }

    @Test
    void manualAnalysisWithAssetCanExposeVisionAnalysisTool() {
        SpringAiOrchestrationService service = buildService(configuredProperties(), mock(), mock());

        Object[] tools = ReflectionTestUtils.invokeMethod(service, "selectTools", "AI_INFERENCE",
                Map.of("assetId", UUID.randomUUID().toString()));

        assertThat(tools).anyMatch(VisionAnalysisTool.class::isInstance);
    }

    @Test
    void systemPromptRequiresStableStructuredMarkdownSections() {
        SpringAiProviderProperties properties = configuredProperties();
        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(callSpec.content()).thenReturn("## 核心结论\n测试结果");
        SpringAiOrchestrationService service = buildService(properties, promptSpec, callSpec);

        service.runIntelligentAnalysis(
                "BUILDING", UUID.randomUUID(), "请综合分析", Map.of(), UUID.randomUUID(), "t");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptSpec).system(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        List<String> headings = List.of(
                "## 核心结论",
                "## 楼栋概况",
                "## 巡检证据",
                "## 风险与优先级",
                "## 视觉病害",
                "## 判断依据",
                "## 人工复核建议",
                "## 能力限制");
        assertThat(prompt).contains(headings.toArray(String[]::new));
        for (int index = 1; index < headings.size(); index++) {
            assertThat(prompt.indexOf(headings.get(index - 1)))
                    .isLessThan(prompt.indexOf(headings.get(index)));
        }
        assertThat(prompt).contains("不输出一级标题");
        assertThat(prompt).contains("不输出 HTML");
        assertThat(prompt).contains("不得修改正式风险等级或更新优先级");
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
    void classifiesInsufficientBalanceSeparatelyFromGenericProviderFailure() {
        assertThat(SpringAiOrchestrationService.providerFailureCode(
                new RuntimeException("402: Insufficient Balance")))
                .isEqualTo(AiErrorCodes.AI_PROVIDER_INSUFFICIENT_BALANCE);
        assertThat(SpringAiOrchestrationService.providerFailureCode(
                new RuntimeException("connection reset")))
                .isEqualTo(AiErrorCodes.AI_PROVIDER_UNAVAILABLE);
    }

    @Test
    void balanceFailureRunsDeterministicEvidenceFallbackUsingBoundVisionResult() {
        SpringAiProviderProperties properties = configuredProperties();
        ChatClient.ChatClientRequestSpec promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(callSpec.content()).thenThrow(new RuntimeException("402: Insufficient Balance"));
        SpringAiOrchestrationService service = buildService(properties, promptSpec, callSpec);

        UUID buildingId = UUID.randomUUID();
        UUID inferenceId = UUID.randomUUID();
        BuildingOverviewTool buildingTool = mock(BuildingOverviewTool.class);
        LatestVisionAnalysisTool latestVisionTool = mock(LatestVisionAnalysisTool.class);
        InspectionEvidenceTool inspectionTool = mock(InspectionEvidenceTool.class);
        RiskAssessmentTool riskTool = mock(RiskAssessmentTool.class);
        RenewalPriorityTool priorityTool = mock(RenewalPriorityTool.class);
        DifyReviewAssistTool reviewTool = mock(DifyReviewAssistTool.class);

        when(buildingTool.overview(buildingId.toString())).thenReturn(new BuildingOverviewTool.BuildingOverviewResult(
                buildingId.toString(), "万科新都会 2号楼", "S059-B002", "武汉市", "FRAME",
                2000, 8, 56, 146, new BigDecimal("77")));
        when(inspectionTool.overview(buildingId.toString())).thenReturn(
                new InspectionEvidenceTool.InspectionOverviewResult(buildingId.toString(), 9, 15, 3));
        when(latestVisionTool.latest(buildingId.toString())).thenReturn(
                new LatestVisionAnalysisTool.LatestVisionResult(
                        buildingId.toString(), inferenceId.toString(), UUID.randomUUID().toString(),
                        "SUCCEEDED", "CONFIRMED", "AI-VISION-LOCAL-001", 3));
        when(riskTool.summary(buildingId.toString())).thenReturn(
                new RiskAssessmentTool.RiskSummaryResult(
                        buildingId.toString(), "万科新都会 2号楼", "万科新都会",
                        "LOW", "32.00", "77.00", "仅辅助"));
        when(priorityTool.priority(buildingId.toString())).thenReturn(
                new RenewalPriorityTool.PriorityResult(
                        buildingId.toString(), "万科新都会 2号楼", "P4", "38.00", "59", "ALL", "CURRENT", "仅辅助"));
        when(reviewTool.run(buildingId.toString())).thenReturn(
                new DifyReviewAssistTool.DifyToolResult(
                        "DIFY-REVIEW-ASSIST-001", "SUCCEEDED", "证据一致，建议人工复核裂缝。",
                        List.of("复核裂缝宽度"), List.of(), 120L));

        ReflectionTestUtils.setField(service, "buildingOverviewTool", buildingTool);
        ReflectionTestUtils.setField(service, "inspectionEvidenceTool", inspectionTool);
        ReflectionTestUtils.setField(service, "latestVisionAnalysisTool", latestVisionTool);
        ReflectionTestUtils.setField(service, "riskAssessmentTool", riskTool);
        ReflectionTestUtils.setField(service, "renewalPriorityTool", priorityTool);
        ReflectionTestUtils.setField(service, "difyReviewAssistTool", reviewTool);

        SpringAiOrchestrationService.IntelligentAnalysisResult result = service.runIntelligentAnalysis(
                "AI_INFERENCE", buildingId, "请综合分析",
                Map.of("sourceInferenceId", inferenceId.toString()), UUID.randomUUID(), "t");

        assertThat(result.status()).isEqualTo(AiAgentExecutionStatus.PARTIAL_SUCCEEDED);
        assertThat(result.answer()).contains("DeepSeek 账户余额不足");
        assertThat(result.answer()).contains("结构化降级证据");
        assertThat(result.answer()).contains("万科新都会 2号楼");
        assertThat(result.answer()).contains("检出数：3");
        assertThat(result.answer()).contains("LOW / 32.00");
        assertThat(result.answer()).contains("P4 / 38.00");
        assertThat(result.answer()).contains("证据一致，建议人工复核裂缝");
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
