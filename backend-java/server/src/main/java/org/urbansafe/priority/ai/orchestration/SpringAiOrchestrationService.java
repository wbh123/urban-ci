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
            - 当上下文已提供刚完成的 REAL 视觉推理编号时，必须复用该结果，
              不得重新调用实时视觉模型；
            - 某个工具不可用（如 Dify 复核辅助）时，明确说明该能力当前不可用，
              并基于已有业务与视觉证据继续给出基础辅助说明，不编造工具结果。

            输出必须使用下面八个 Markdown 二级标题，并严格按此顺序组织；即使某一项没有数据，
            也必须保留对应标题并写明“暂无数据”“信息不足”或具体不可用原因：
            ## 核心结论
            ## 楼栋概况
            ## 巡检证据
            ## 风险与优先级
            ## 视觉病害
            ## 判断依据
            ## 人工复核建议
            ## 能力限制

            输出格式要求：
            - “核心结论”控制在 2 至 4 句，优先概括最重要事实、风险线索和不确定性；
            - 楼栋概况、风险与优先级等紧凑事实优先使用 Markdown 表格；
            - 巡检证据、判断依据、人工复核建议优先使用简短项目列表；
            - 视觉病害必须继续使用“疑似”措辞；若工具提供置信度，应如实保留，不得自行提高或改写；
            - 工具失败或能力关闭统一放在“能力限制”，不要在每个章节重复长篇说明；
            - 不输出一级标题，不重复八个固定标题，不输出 HTML，不输出 JSON，不使用代码围栏；
            - 不在结尾追加“如需我可继续”“请告知”等邀请性文字；
            - 内容应紧凑、可扫描，避免同一事实在多个章节反复叙述。
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
            String providerErrorCode = providerFailureCode(ex);
            if (AiErrorCodes.AI_PROVIDER_INSUFFICIENT_BALANCE.equals(providerErrorCode)) {
                log.warn("Spring AI / DeepSeek 余额不足，切换结构化证据降级路径: {}", safeProviderMessage(ex));
                finalStatus = AiAgentExecutionStatus.PARTIAL_SUCCEEDED;
                answer = hasToolSteps(execution)
                        ? buildBalanceFallbackFromExistingSteps(execution)
                        : buildDeterministicEvidenceFallback(businessType, businessId, context);
                execution.setErrorCode(providerErrorCode);
                execution.setErrorMessage("DeepSeek 账户余额不足，未生成新的语言模型综合结论");
            } else {
                log.warn("Spring AI 智能编排失败", ex);
                finalStatus = AiAgentExecutionStatus.FAILED;
                answer = "DeepSeek 智能编排当前不可用，无法生成新的综合研判。已获得的结构化工具结果摘要：\n"
                        + summarizeToolSteps(execution)
                        + "\n基础业务、原始证据与人工复核仍可继续使用。";
                execution.setErrorCode(providerErrorCode);
                execution.setErrorMessage(safeProviderMessage(ex));
            }
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
        boolean boundToCompletedVision = context != null && context.get("sourceInferenceId") != null;
        if (context != null && context.get("assetId") != null && !boundToCompletedVision) {
            tools.add(visionAnalysisTool);
        }
        if (automationSettingsService.intelligentWorkflowEnabled()
                && ("BUILDING".equalsIgnoreCase(businessType)
                || "AI_INFERENCE".equalsIgnoreCase(businessType))) {
            tools.add(difyReviewAssistTool);
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
        if (context != null && context.get("sourceInferenceId") != null) {
            prompt.append("本次综合研判已绑定刚完成的 REAL 视觉推理（sourceInferenceId），")
                    .append("必须复用该推理结果，不得重新调用实时视觉模型。\n");
        }
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

    /**
     * DeepSeek 首轮请求因余额不足而没有机会触发 Tool Calling 时，直接执行核心只读 Tool。
     * 这不是语言模型综合结论，而是可审计的确定性结构化证据快照。
     */
    private String buildDeterministicEvidenceFallback(
            String businessType, UUID businessId, Map<String, Object> context) {
        UUID buildingId = resolveBuildingId(businessType, businessId, context);
        if (buildingId == null) {
            return "## 核心结论\nDeepSeek 账户余额不足，无法生成新的语言模型综合结论。\n\n"
                    + "## 楼栋概况\n暂无可确定的楼栋标识。\n\n"
                    + "## 巡检证据\n暂无结构化降级证据。\n\n"
                    + "## 风险与优先级\n暂无结构化降级证据。\n\n"
                    + "## 视觉病害\n暂无结构化降级证据。\n\n"
                    + "## 判断依据\n仅记录供应商计费能力不可用，不编造分析。\n\n"
                    + "## 人工复核建议\n可继续依据页面原始业务数据进行人工复核。\n\n"
                    + "## 能力限制\nDeepSeek 402 Insufficient Balance；未调用语言模型生成结论。";
        }

        String id = buildingId.toString();
        BuildingOverviewTool.BuildingOverviewResult building = safeCall(() -> buildingOverviewTool.overview(id));
        InspectionEvidenceTool.InspectionOverviewResult inspection = safeCall(() -> inspectionEvidenceTool.overview(id));
        LatestVisionAnalysisTool.LatestVisionResult vision = safeCall(() -> latestVisionAnalysisTool.latest(id));
        RiskAssessmentTool.RiskSummaryResult risk = safeCall(() -> riskAssessmentTool.summary(id));
        RenewalPriorityTool.PriorityResult priority = safeCall(() -> renewalPriorityTool.priority(id));
        DifyReviewAssistTool.DifyToolResult review = null;
        if (automationSettingsService.intelligentWorkflowEnabled()
                && ("BUILDING".equalsIgnoreCase(businessType)
                || "AI_INFERENCE".equalsIgnoreCase(businessType))) {
            review = safeCall(() -> difyReviewAssistTool.run(id));
        }

        StringBuilder out = new StringBuilder();
        out.append("## 核心结论\n")
                .append("DeepSeek 账户余额不足，本次未生成新的语言模型综合结论。")
                .append("以下内容为系统直接读取的结构化降级证据，可继续用于人工复核，但不替代专业结论。\n\n");

        out.append("## 楼栋概况\n");
        if (building == null) {
            out.append("楼栋档案读取失败或不可用。\n\n");
        } else {
            out.append("- 名称：").append(value(building.buildingName())).append("（")
                    .append(value(building.buildingCode())).append("）\n")
                    .append("- 地址：").append(value(building.address())).append("\n")
                    .append("- 结构/建成年代：").append(value(building.structureType())).append(" / ")
                    .append(value(building.constructionYear())).append("\n")
                    .append("- 楼层/户数/人数：").append(value(building.floorCount())).append(" / ")
                    .append(value(building.householdCount())).append(" / ")
                    .append(value(building.residentCount())).append("\n")
                    .append("- 档案完整度：").append(value(building.archiveCompletenessScore())).append("\n\n");
        }

        out.append("## 巡检证据\n");
        if (inspection == null) {
            out.append("巡检证据概况读取失败或不可用。\n\n");
        } else {
            out.append("- 巡检任务：").append(inspection.inspectionTaskCount()).append("\n")
                    .append("- 巡检记录：").append(inspection.inspectionRecordCount()).append("\n")
                    .append("- 证据数量：").append(inspection.evidenceCount()).append("\n\n");
        }

        out.append("## 风险与优先级\n");
        if (risk == null && priority == null) {
            out.append("正式风险与更新优先级读取失败或不可用。\n\n");
        } else {
            out.append("- 正式风险：")
                    .append(risk == null ? "暂无" : value(risk.riskLevel()) + " / " + value(risk.riskScore()))
                    .append("\n")
                    .append("- 更新优先级：")
                    .append(priority == null ? "暂无" : value(priority.priorityLevel()) + " / " + value(priority.priorityScore()))
                    .append("\n\n");
        }

        out.append("## 视觉病害\n");
        if (vision == null) {
            out.append("REAL 视觉结果读取失败或不可用。\n\n");
        } else {
            out.append("- 绑定推理：").append(value(vision.inferenceId())).append("\n")
                    .append("- 图片资产：").append(value(vision.assetId())).append("\n")
                    .append("- 状态/复核：").append(value(vision.status())).append(" / ")
                    .append(value(vision.reviewStatus())).append("\n")
                    .append("- 模型：").append(value(vision.modelId())).append("\n")
                    .append("- 检出数：").append(vision.detectionCount()).append("\n\n");
        }

        out.append("## 判断依据\n")
                .append("- 本节仅汇总 Spring Boot 只读 Tool 与已持久化 REAL 视觉结果。\n")
                .append("- 未使用 DeepSeek 对证据进行新的语言模型推断。\n");
        if (review != null) {
            out.append("- Dify Review Assist：").append(value(review.status())).append("；")
                    .append(value(review.summary())).append("\n");
        }
        out.append("\n");

        out.append("## 人工复核建议\n");
        if (review != null && review.recommendations() != null && !review.recommendations().isEmpty()) {
            for (String item : review.recommendations()) {
                out.append("- ").append(item).append("\n");
            }
        } else {
            out.append("- 继续结合原始巡检图片、检测框、现场记录和正式风险结果进行人工确认。\n");
        }
        out.append("\n## 能力限制\n")
                .append("- DeepSeek 返回 402 Insufficient Balance，当前账户余额不足。\n")
                .append("- 本页展示的是结构化降级证据，不是新的 DeepSeek 综合研判结论。\n")
                .append("- 正式风险评分和更新优先级未被本次降级流程修改。\n");
        return out.toString();
    }

    private static String buildBalanceFallbackFromExistingSteps(AiAgentExecution execution) {
        return "## 核心结论\nDeepSeek 账户余额不足，未能完成新的语言模型综合归纳。\n\n"
                + "## 楼栋概况\n已执行工具结果请见下方执行轨迹。\n\n"
                + "## 巡检证据\n已执行工具结果请见下方执行轨迹。\n\n"
                + "## 风险与优先级\n已执行工具结果请见下方执行轨迹。\n\n"
                + "## 视觉病害\n已执行工具结果请见下方执行轨迹。\n\n"
                + "## 判断依据\n结构化工具执行摘要：\n" + summarizeToolSteps(execution) + "\n"
                + "## 人工复核建议\n继续依据已获得的业务与视觉证据完成人工复核。\n\n"
                + "## 能力限制\nDeepSeek 402 Insufficient Balance；未完成最终语言模型归纳。";
    }

    private static boolean hasToolSteps(AiAgentExecution execution) {
        return execution.steps().stream().anyMatch(step -> "TOOL".equals(step.type().name()));
    }

    static String providerFailureCode(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 8) {
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (message.contains("402")
                    && (message.contains("insufficient balance")
                    || message.contains("insufficient_balance")
                    || message.contains("balance"))) {
                return AiErrorCodes.AI_PROVIDER_INSUFFICIENT_BALANCE;
            }
            current = current.getCause();
        }
        return AiErrorCodes.AI_PROVIDER_UNAVAILABLE;
    }

    private static String safeProviderMessage(Throwable throwable) {
        Throwable current = throwable;
        String last = null;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                last = current.getMessage();
            }
            current = current.getCause();
        }
        if (last == null) {
            return throwable == null ? "Unknown provider error" : throwable.getClass().getSimpleName();
        }
        return last.length() <= 1000 ? last : last.substring(0, 1000);
    }

    private static UUID resolveBuildingId(String businessType, UUID businessId, Map<String, Object> context) {
        UUID contextBuildingId = uuid(context == null ? null : context.get("buildingId"));
        if (contextBuildingId != null) {
            return contextBuildingId;
        }
        if ("BUILDING".equalsIgnoreCase(businessType)
                || "AI_INFERENCE".equalsIgnoreCase(businessType)
                || "RISK_ASSESSMENT".equalsIgnoreCase(businessType)) {
            return businessId;
        }
        return null;
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String value(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "暂无" : String.valueOf(value);
    }

    private static <T> T safeCall(java.util.concurrent.Callable<T> call) {
        try {
            return call.call();
        } catch (RuntimeException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
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
