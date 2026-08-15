package org.urbansafe.priority.ai.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.config.SpringAiProviderProperties;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentExecutionRepository;
import org.urbansafe.priority.ai.execution.AiAgentExecutionStatus;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
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

/**
 * Spring AI 智能编排服务。
 *
 * <p>以 DeepSeek 为推理模型（ChatModel），通过 Tool Calling 在受限工具集内决定
 * 是否调用本地视觉 / Dify 工作流 / 只读业务工具。执行轨迹记录为
 * AiAgentExecution + 步骤（Tool Execution Trace，不记录模型私有思维过程）。
 *
 * <p>确定性能力调用（单一路由）仍由 AiOrchestrationService / AiProviderRouter 负责；
 * 本服务位于其上提供“智能综合分析”。
 */
@Service
public class SpringAiOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiOrchestrationService.class);

    private static final String SYSTEM_PROMPT = """
            你是城市建筑安全辅助研判助手。

            你只能基于：
            1. 系统提供的业务数据；
            2. 工具调用结果；
            3. 当前授权范围内的知识证据；
            进行辅助分析。

            必须遵守：
            - 不得编造不存在的巡检事实；
            - 不得把视觉模型输出描述为专业确认结果，必须使用“疑似”措辞；
            - 不得修改正式风险等级或更新优先级；
            - 不得替代专业人员作出正式结论；
            - 证据不足时应明确说明信息不足并建议补充资料；
            - 某个工具不可用（如 Dify 复核辅助）时，明确说明该能力当前不可用，
              并基于已有业务与视觉证据继续给出基础辅助说明，不编造工具结果。
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final SpringAiProviderProperties springAiProperties;
    private final AiAgentExecutionRepository executionRepository;
    private final AiAutomationSettingsService automationSettingsService;

    private final VisionAnalysisTool visionAnalysisTool;
    private final BuildingOverviewTool buildingOverviewTool;
    private final RiskAssessmentTool riskAssessmentTool;
    private final RenewalPriorityTool renewalPriorityTool;
    private final InspectionEvidenceTool inspectionEvidenceTool;
    private final LatestVisionAnalysisTool latestVisionAnalysisTool;
    private final DifyReviewAssistTool difyReviewAssistTool;
    private final DifyReportDraftTool difyReportDraftTool;
    private final KnowledgeRetrievalTool knowledgeRetrievalTool;

    public SpringAiOrchestrationService(
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            SpringAiProviderProperties springAiProperties,
            AiAgentExecutionRepository executionRepository,
            AiAutomationSettingsService automationSettingsService,
            VisionAnalysisTool visionAnalysisTool,
            BuildingOverviewTool buildingOverviewTool,
            RiskAssessmentTool riskAssessmentTool,
            RenewalPriorityTool renewalPriorityTool,
            InspectionEvidenceTool inspectionEvidenceTool,
            LatestVisionAnalysisTool latestVisionAnalysisTool,
            DifyReviewAssistTool difyReviewAssistTool,
            DifyReportDraftTool difyReportDraftTool,
            KnowledgeRetrievalTool knowledgeRetrievalTool) {
        this.chatClientBuilder = chatClientBuilder;
        this.springAiProperties = springAiProperties;
        this.executionRepository = executionRepository;
        this.automationSettingsService = automationSettingsService;
        this.visionAnalysisTool = visionAnalysisTool;
        this.buildingOverviewTool = buildingOverviewTool;
        this.riskAssessmentTool = riskAssessmentTool;
        this.renewalPriorityTool = renewalPriorityTool;
        this.inspectionEvidenceTool = inspectionEvidenceTool;
        this.latestVisionAnalysisTool = latestVisionAnalysisTool;
        this.difyReviewAssistTool = difyReviewAssistTool;
        this.difyReportDraftTool = difyReportDraftTool;
        this.knowledgeRetrievalTool = knowledgeRetrievalTool;
    }

    /** DeepSeek 文本能力是否配置就绪（Key 为空时 false → UNCONFIGURED）。 */
    public boolean configured() {
        return springAiProperties.isEnabled() && springAiProperties.configured()
                && chatClientBuilder.getIfAvailable() != null;
    }

    /** 运行一次智能综合分析。文本能力不可用时返回可审计的业务降级结果，而不是阻断页面。 */
    public IntelligentAnalysisResult runIntelligentAnalysis(
            String businessType,
            UUID businessId,
            String question,
            Map<String, Object> context,
            UUID requestedBy,
            String requestedByName) {
        AiAgentExecution execution = new AiAgentExecution(
                UUID.randomUUID(), businessType, businessId,
                question == null || question.isBlank() ? "综合分析" : question,
                requestedBy, requestedByName);
        execution.setStatus(AiAgentExecutionStatus.RUNNING);
        executionRepository.create(execution);
        AiAgentTrace.begin(execution);

        long overallStarted = System.nanoTime();
        String answer;
        AiAgentExecutionStatus finalStatus;
        try {
            if (!configured()) {
                finalStatus = AiAgentExecutionStatus.PARTIAL_SUCCEEDED;
                answer = buildTextUnavailableFallback(businessType, context);
                execution.setErrorCode(AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED);
                execution.setErrorMessage("Spring AI / DeepSeek 文本能力未配置");
            } else {
                Object[] tools = selectTools(businessType, context);
                ChatClient client = chatClientBuilder.getObject().clone().defaultTools(tools).build();
                long llmStarted = System.nanoTime();
                String content = client.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(buildUserPrompt(businessType, businessId, question, context))
                        .call()
                        .content();
                long llmMs = Math.max(0L, (System.nanoTime() - llmStarted) / 1_000_000L);
                AiAgentTrace.recordLlm(springAiProperties.getModel(), llmMs);
                answer = content == null ? "未生成分析结果。" : content;
                finalStatus = resolveFinalStatus(execution.steps());
            }
        } catch (RuntimeException ex) {
            log.warn("Spring AI 智能编排失败：{}", ex.getMessage());
            finalStatus = AiAgentExecutionStatus.FAILED;
            answer = "DeepSeek 智能编排当前不可用，无法生成新的综合研判。已获得的结构化工具结果摘要：\n"
                    + summarizeToolSteps(execution)
                    + "\n基础业务、原始证据与人工复核仍可继续使用。";
            execution.setErrorCode(AiErrorCodes.AI_PROVIDER_UNAVAILABLE);
            execution.setErrorMessage(ex.getMessage());
        } finally {
            AiAgentTrace.end();
        }

        long durationMs = Math.max(0L, (System.nanoTime() - overallStarted) / 1_000_000L);
        execution.setStatus(finalStatus);
        execution.setFinishedAt(Instant.now());
        execution.setDurationMs(durationMs);
        execution.setSummary(answer);
        executionRepository.appendSteps(execution.id(), execution.steps());
        executionRepository.complete(
                execution.id(), finalStatus, durationMs, answer,
                execution.modelCode(), execution.errorCode(), execution.errorMessage());
        return IntelligentAnalysisResult.from(execution, finalStatus, answer, durationMs);
    }

    public AiAgentExecution getExecution(UUID executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new AiProviderException(
                        AiErrorCodes.AI_EXECUTION_NOT_FOUND, "智能编排执行记录不存在"));
    }

    /** 按业务场景选择受限工具集；业务开关关闭时不把对应 Tool 暴露给模型。 */
    private Object[] selectTools(String businessType, Map<String, Object> context) {
        List<Object> tools = new ArrayList<>();
        tools.add(buildingOverviewTool);
        tools.add(inspectionEvidenceTool);
        tools.add(latestVisionAnalysisTool);
        tools.add(riskAssessmentTool);
        tools.add(renewalPriorityTool);
        if (automationSettingsService.knowledgeQaEnabled()) {
            tools.add(knowledgeRetrievalTool);
        }
        if (context != null && context.get("assetId") != null) {
            tools.add(visionAnalysisTool);
        }
        if (automationSettingsService.intelligentWorkflowEnabled()
                && ("BUILDING".equalsIgnoreCase(businessType)
                || "AI_INFERENCE".equalsIgnoreCase(businessType))) {
            tools.add(difyReviewAssistTool);
            tools.add(difyReportDraftTool);
        }
        return tools.toArray();
    }

    private static String buildUserPrompt(
            String businessType,
            UUID businessId,
            String question,
            Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请对以下业务对象进行综合分析。\n");
        prompt.append("业务类型：").append(businessType == null ? "UNKNOWN" : businessType).append("\n");
        if (businessId != null) {
            prompt.append("业务楼栋ID：").append(businessId).append("\n");
        }
        appendContext(prompt, context, "buildingId", "上下文楼栋ID");
        appendContext(prompt, context, "assetId", "现有巡检图片资产ID");
        appendContext(prompt, context, "riskLevel", "正式风险等级");
        appendContext(prompt, context, "riskScore", "正式风险分");
        appendContext(prompt, context, "priorityLevel", "更新优先级");
        appendContext(prompt, context, "freshness", "风险结果新鲜度");
        prompt.append("用户问题：").append(question == null ? "综合分析" : question).append("\n");
        return prompt.toString();
    }

    private static void appendContext(
            StringBuilder prompt,
            Map<String, Object> context,
            String key,
            String label) {
        if (context == null || context.get(key) == null) return;
        prompt.append(label).append("：").append(context.get(key)).append("\n");
    }

    private static String buildTextUnavailableFallback(String businessType, Map<String, Object> context) {
        if ("AI_INFERENCE".equalsIgnoreCase(businessType)) {
            return "智能文本能力暂不可用，已保留现有 AI 视觉识别和业务证据供人工复核。"
                    + "请依据原始图片、疑似病害区域、巡检记录及正式风险结果完成人工确认、修正或驳回；"
                    + "AI 不会自动提交人工复核结论。";
        }
        if ("RISK_ASSESSMENT".equalsIgnoreCase(businessType)) {
            return "智能文本能力暂不可用，正式风险评分与更新优先级不受影响。"
                    + "请继续依据页面中的确定性评分因子、数据新鲜度和人工复核状态进行治理研判。";
        }
        if ("FEEDBACK".equalsIgnoreCase(businessType)) {
            return "智能文本能力暂不可用，可继续使用基础规则对公众反馈进行辅助分流，处理状态仍由人工确认。";
        }
        if (context != null && context.get("assetId") != null) {
            return "智能文本能力暂不可用，现有图片与结构化识别结果仍可继续查看，基础业务不受影响。";
        }
        return "智能文本能力暂不可用，当前页面仍可继续使用已有业务数据和确定性结果，需由人工完成最终判断。";
    }

    /** 任一工具步骤 FAILED（预期能力失败）→ PARTIAL_SUCCEEDED；否则 SUCCEEDED。 */
    static AiAgentExecutionStatus resolveFinalStatus(List<org.urbansafe.priority.ai.execution.AiAgentExecutionStep> steps) {
        boolean anyToolFailed = steps.stream()
                .anyMatch(step -> step.type().name().equals("TOOL")
                        && step.status() == AiAgentStepStatus.FAILED);
        return anyToolFailed ? AiAgentExecutionStatus.PARTIAL_SUCCEEDED : AiAgentExecutionStatus.SUCCEEDED;
    }

    private static String summarizeToolSteps(AiAgentExecution execution) {
        StringBuilder summary = new StringBuilder();
        for (var step : execution.steps()) {
            summary.append("- ").append(step.toolName()).append("：")
                    .append(step.status().name()).append("\n");
        }
        return summary.length() == 0 ? "（尚未获得任何工具结果）" : summary.toString();
    }

    public record IntelligentAnalysisResult(
            UUID executionId,
            AiAgentExecutionStatus status,
            String answer,
            List<AiAgentExecutionStepPublic> steps,
            long durationMs,
            String modelCode) {

        public IntelligentAnalysisResult {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }

        public static IntelligentAnalysisResult from(
                AiAgentExecution execution,
                AiAgentExecutionStatus status,
                String answer,
                long durationMs) {
            return new IntelligentAnalysisResult(
                    execution.id(), status, answer,
                    AiAgentExecutionStepPublic.from(execution.steps()),
                    durationMs, execution.modelCode());
        }
    }

    /** 对外只暴露可展示字段的步骤（不包含敏感细节）。 */
    public record AiAgentExecutionStepPublic(
            int seqNo,
            String type,
            String toolName,
            String provider,
            String status,
            Long durationMs,
            String errorCode,
            String detail) {

        public static List<AiAgentExecutionStepPublic> from(List<org.urbansafe.priority.ai.execution.AiAgentExecutionStep> steps) {
            return steps.stream()
                    .map(step -> new AiAgentExecutionStepPublic(
                            step.seqNo(),
                            step.type().name(),
                            step.toolName(),
                            step.provider(),
                            step.status().name(),
                            step.durationMs(),
                            step.errorCode(),
                            step.detail()))
                    .toList();
        }
    }
}
