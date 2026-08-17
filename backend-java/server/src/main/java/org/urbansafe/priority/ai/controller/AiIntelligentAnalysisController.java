package org.urbansafe.priority.ai.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiExecutionCommand;
import org.urbansafe.priority.ai.execution.AiExecutionTaskQueryService;
import org.urbansafe.priority.ai.execution.AiExecutionTaskService;
import org.urbansafe.priority.ai.execution.AiIntelligentAnalysisExecutionTaskExecutor;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;

/**
 * Spring AI 智能综合分析接口。
 *
 * <p>前端只允许提交业务标识与问题（businessType/businessId/question/context），
 * 不允许提交 apiKey、baseUrl、systemPrompt 或任意工具名；系统提示、工具集与
 * 安全上下文一律由后端决定。
 */
@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class AiIntelligentAnalysisController {

    /** 自动研判契约升级时修改该版本，使旧错误任务不再永久命中幂等键。 */
    private static final String AUTOMATIC_ANALYSIS_CONTRACT_REVISION = "source-bound-v2";
    private static final List<String> SAFE_CONTEXT_KEYS = List.of(
            "assetId", "sourceInferenceId", "buildingId", "riskLevel", "riskScore", "priorityLevel", "freshness");

    private final SpringAiOrchestrationService orchestrationService;
    private final AiExecutionTaskService executionTaskService;
    private final AiExecutionTaskQueryService executionTaskQueryService;

    public AiIntelligentAnalysisController(
            SpringAiOrchestrationService orchestrationService,
            AiExecutionTaskService executionTaskService,
            AiExecutionTaskQueryService executionTaskQueryService) {
        this.orchestrationService = orchestrationService;
        this.executionTaskService = executionTaskService;
        this.executionTaskQueryService = executionTaskQueryService;
    }

    /**
     * 兼容旧调用方的同步接口。比赛演示前端改用 /ai-intelligent-analysis/tasks。
     */
    @PostMapping("/ai-intelligent-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXPERT', 'PROFESSIONAL_REVIEWER')")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> body) {
        String businessType = string(body.get("businessType"));
        UUID businessId = uuid(body.get("businessId"));
        Map<String, Object> context = safeContext(businessType, body.get("context"), businessId);
        SpringAiOrchestrationService.IntelligentAnalysisResult result =
                orchestrationService.runIntelligentAnalysis(
                        businessType,
                        businessId,
                        string(body.get("question")),
                        context,
                        CurrentUser.getUserId(),
                        CurrentUser.getUsername());
        return ResponseEntity.ok(success(resultData(result)));
    }

    /** 提交持久化异步综合研判任务；接口只负责入队，不等待大模型完成。 */
    @PostMapping("/ai-intelligent-analysis/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXPERT', 'PROFESSIONAL_REVIEWER')")
    public ResponseEntity<Map<String, Object>> submitTask(@RequestBody Map<String, Object> body) {
        String businessType = string(body.get("businessType"));
        UUID businessId = uuid(body.get("businessId"));
        String question = string(body.get("question"));
        Map<String, Object> context = safeContext(businessType, body.get("context"), businessId);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("businessType", businessType);
        if (businessId != null) {
            inputs.put("businessId", businessId.toString());
        }
        inputs.put("question", question == null ? "综合分析" : question);
        inputs.put("context", context);
        inputs.put("requestedByName", CurrentUser.getUsername());
        inputs.put("taskType", "INTELLIGENT_ANALYSIS");

        UUID taskId = executionTaskService.enqueue(new AiExecutionCommand(
                uuid(context.get("assetId")),
                AiIntelligentAnalysisExecutionTaskExecutor.WORKFLOW_CODE,
                "REAL",
                "spring-ai-deepseek",
                "SPRING_AI",
                "TEXT_GENERATION",
                question,
                analysisIdempotencyKey(context),
                CurrentUser.getUserId(),
                inputs));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", taskId);
        data.put("status", "PENDING");
        data.put("pollAfterMs", 1000);
        return ResponseEntity.accepted().body(success(data));
    }

    /** 查询异步综合研判任务；完成后直接附带原同步接口格式的 result。 */
    @GetMapping("/ai-intelligent-analysis/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXPERT', 'PROFESSIONAL_REVIEWER')")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable UUID taskId) {
        Map<String, Object> task = new LinkedHashMap<>(executionTaskQueryService.get(taskId));
        Object resultId = task.remove("inferenceId");
        if ("SUCCEEDED".equals(task.get("status")) && resultId != null) {
            UUID executionId = uuid(resultId);
            if (executionId != null) {
                task.put("executionId", executionId);
                task.put("result", executionData(orchestrationService.getExecution(executionId)));
            }
        }
        return ResponseEntity.ok(success(task));
    }

    @GetMapping("/ai-intelligent-analysis/executions/{executionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXPERT', 'PROFESSIONAL_REVIEWER')")
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable UUID executionId) {
        return ResponseEntity.ok(success(executionData(orchestrationService.getExecution(executionId))));
    }

    static Map<String, Object> safeContext(
            String businessType,
            Object rawContext,
            UUID businessId) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (rawContext instanceof Map<?, ?> contextMap) {
            for (String key : SAFE_CONTEXT_KEYS) {
                Object value = contextMap.get(key);
                if (isSafeScalar(value)) {
                    context.put(key, value instanceof String ? String.valueOf(value).trim() : value);
                }
            }
        }
        if (businessId != null
                && !context.containsKey("buildingId")
                && isBuildingBusiness(businessType)) {
            context.put("buildingId", businessId.toString());
        }
        return context;
    }

    static String analysisIdempotencyKey(Map<String, Object> context) {
        UUID sourceInferenceId = uuid(context == null ? null : context.get("sourceInferenceId"));
        if (sourceInferenceId != null) {
            return "intelligent-analysis:" + AUTOMATIC_ANALYSIS_CONTRACT_REVISION
                    + ":source-inference:" + sourceInferenceId;
        }
        return "intelligent-analysis:" + UUID.randomUUID();
    }

    private static Map<String, Object> resultData(
            SpringAiOrchestrationService.IntelligentAnalysisResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("executionId", result.executionId());
        data.put("status", result.status().name());
        data.put("answer", result.answer());
        data.put("steps", result.steps());
        data.put("durationMs", result.durationMs());
        data.put("modelCode", result.modelCode());
        return data;
    }

    private static Map<String, Object> executionData(AiAgentExecution execution) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("executionId", execution.id());
        data.put("businessType", execution.businessType());
        data.put("businessId", execution.businessId());
        data.put("question", execution.question());
        data.put("status", execution.status().name());
        data.put("answer", execution.summary());
        data.put("modelCode", execution.modelCode());
        data.put("durationMs", execution.durationMs());
        data.put("summary", execution.summary());
        data.put("errorCode", execution.errorCode());
        data.put("errorMessage", execution.errorMessage());
        data.put("startedAt", execution.startedAt());
        data.put("finishedAt", execution.finishedAt());
        data.put("steps", SpringAiOrchestrationService.AiAgentExecutionStepPublic.from(execution.steps()));
        return data;
    }

    private static boolean isBuildingBusiness(String businessType) {
        return "BUILDING".equalsIgnoreCase(businessType)
                || "AI_INFERENCE".equalsIgnoreCase(businessType)
                || "RISK_ASSESSMENT".equalsIgnoreCase(businessType);
    }

    private static boolean isSafeScalar(Object value) {
        if (value == null) return false;
        if (value instanceof Number || value instanceof Boolean) return true;
        return value instanceof String text && !text.isBlank() && text.length() <= 512;
    }

    private static Map<String, Object> success(Object data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success());
        body.put("data", data);
        body.put("error", null);
        body.put("requestId", metadata.requestId());
        body.put("timestamp", metadata.timestamp());
        return body;
    }

    private static String string(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
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
}
