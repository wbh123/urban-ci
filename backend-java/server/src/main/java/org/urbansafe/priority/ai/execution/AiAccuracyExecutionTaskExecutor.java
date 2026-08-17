package org.urbansafe.priority.ai.execution;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.repository.AiInferenceRepository;
import org.urbansafe.priority.ai.vision.VisionAnalysisAuditRepository;
import org.urbansafe.priority.ai.vision.VisionAnalysisOrchestrator;
import org.urbansafe.priority.ai.vision.VisionAnalysisOutcome;
import org.urbansafe.priority.ai.vision.VisionAnalysisRequest;
import org.urbansafe.priority.asset.service.Phase2AssetService;

/** ACCURACY 专用异步执行器；前端请求只负责入队，不等待分钟级模型调用。 */
@Service
public class AiAccuracyExecutionTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(AiAccuracyExecutionTaskExecutor.class);
    private static final String AUTO_ANALYSIS_QUESTION =
            "请基于刚完成的真实视觉识别，立即结合楼栋档案、巡检证据、正式风险与更新优先级进行综合研判。"
                    + "对细小、低置信度病害保持较高筛查敏感度，但必须使用疑似措辞，不得修改正式风险评分。";

    private static final Set<String> RETRYABLE = Set.of(
            "AI_SERVICE_TIMEOUT",
            "AI_SERVICE_UNAVAILABLE",
            AiErrorCodes.AI_PROVIDER_TIMEOUT,
            AiErrorCodes.AI_PROVIDER_UNAVAILABLE);

    private final AiExecutionTaskRepository executionRepository;
    private final AiInferenceRepository inferenceRepository;
    private final Phase2AssetService assetService;
    private final VisionAnalysisOrchestrator orchestrator;
    private final VisionAnalysisAuditRepository auditRepository;
    private final AiExecutionProperties properties;

    public AiAccuracyExecutionTaskExecutor(
            AiExecutionTaskRepository executionRepository,
            AiInferenceRepository inferenceRepository,
            Phase2AssetService assetService,
            VisionAnalysisOrchestrator orchestrator,
            VisionAnalysisAuditRepository auditRepository,
            AiExecutionProperties properties) {
        this.executionRepository = executionRepository;
        this.inferenceRepository = inferenceRepository;
        this.assetService = assetService;
        this.orchestrator = orchestrator;
        this.auditRepository = auditRepository;
        this.properties = properties;
    }

    public void execute(AiExecutionTask task) {
        UUID inferenceId = null;
        try {
            Map<String, Object> model = inferenceRepository.findModelByCode(task.modelId())
                    .orElseThrow(() -> new IllegalStateException("ACCURACY 请求模型未登记"));
            if (!"REAL".equals(String.valueOf(model.get("mode")))
                    || !"APPROVED".equals(String.valueOf(model.get("status")))) {
                throw new IllegalStateException("ACCURACY 基础视觉模型未通过正式准入");
            }
            Map<String, Object> trace = inferenceRepository.resolveAssetTraceability(task.assetId())
                    .orElseThrow(() -> new IllegalStateException("ACCURACY 图片缺少业务追溯关系"));
            Map<String, Object> asset = assetService.get(task.assetId());
            byte[] imageBytes = assetService.content(task.assetId());
            UUID buildingId = (UUID) trace.get("buildingId");

            inferenceId = UUID.randomUUID();
            String requestCode = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            inferenceRepository.insertTask(
                    inferenceId,
                    requestCode,
                    task.idempotencyKey(),
                    task.assetId(),
                    (UUID) trace.get("inspectionTaskId"),
                    (UUID) trace.get("inspectionRecordId"),
                    buildingId,
                    (UUID) trace.get("communityId"),
                    (UUID) model.get("id"),
                    "REAL",
                    1,
                    task.requestedBy());
            if (inferenceRepository.markRunning(inferenceId) == 0) {
                throw new IllegalStateException("ACCURACY 正式推理任务无法进入 RUNNING");
            }

            String triggerType = value(task.inputs().get("triggerType"), "MANUAL_SINGLE");
            VisionAnalysisOutcome outcome = orchestrator.analyze(new VisionAnalysisRequest(
                    requestCode,
                    task.modelId(),
                    String.valueOf(task.assetId()),
                    String.valueOf(asset.getOrDefault("originalFilename", "inspection-image")),
                    String.valueOf(asset.getOrDefault("contentType", "image/jpeg")),
                    imageBytes,
                    triggerType));
            auditRepository.record(task.id(), outcome);
            AiInferenceResponse response = outcome.response();
            inferenceRepository.saveSuccess(inferenceId, response);
            if ("REJECTED".equals(response.status())) {
                inferenceRepository.saveFailure(
                        inferenceId,
                        "AI_IMAGE_NOT_APPLICABLE",
                        "图片不适用于当前高精度视觉模型",
                        true);
                executionRepository.markRejected(task.id(), inferenceId, "图片不适用于当前高精度视觉模型");
            } else {
                executionRepository.markSucceeded(task.id(), inferenceId);
                enqueueAutomaticIntelligentAnalysis(task, inferenceId, buildingId);
            }
        } catch (AiProviderException ex) {
            if (inferenceId != null) {
                inferenceRepository.saveFailure(
                        inferenceId,
                        ex.getErrorCode(),
                        ex.getMessage(),
                        inferenceRepository.isImageRejection(ex.getErrorCode()));
            }
            failOrRetry(task, ex.getErrorCode(), safe(ex), RETRYABLE.contains(ex.getErrorCode()));
        } catch (RuntimeException ex) {
            if (inferenceId != null) {
                inferenceRepository.saveFailure(
                        inferenceId,
                        "AI_EXECUTION_UNEXPECTED",
                        safe(ex),
                        false);
            }
            failOrRetry(task, "AI_EXECUTION_UNEXPECTED", safe(ex), true);
        }
    }

    private void enqueueAutomaticIntelligentAnalysis(
            AiExecutionTask visualTask,
            UUID sourceInferenceId,
            UUID buildingId) {
        if (sourceInferenceId == null || buildingId == null || visualTask.requestedBy() == null) {
            return;
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("assetId", visualTask.assetId().toString());
        context.put("buildingId", buildingId.toString());
        context.put("sourceInferenceId", sourceInferenceId.toString());

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("businessType", "AI_INFERENCE");
        inputs.put("businessId", buildingId.toString());
        inputs.put("question", AUTO_ANALYSIS_QUESTION);
        inputs.put("context", context);
        inputs.put("taskType", "INTELLIGENT_ANALYSIS");
        inputs.put("triggerType", "VISION_SUCCEEDED_AUTO");

        String idempotencyKey = "intelligent-analysis:source-inference:" + sourceInferenceId;
        try {
            executionRepository.enqueue(new AiExecutionCommand(
                    visualTask.assetId(),
                    AiIntelligentAnalysisExecutionTaskExecutor.WORKFLOW_CODE,
                    "REAL",
                    "spring-ai-deepseek",
                    "SPRING_AI",
                    "TEXT_GENERATION",
                    AUTO_ANALYSIS_QUESTION,
                    idempotencyKey,
                    visualTask.requestedBy(),
                    inputs));
            log.info("自动综合研判已入队 sourceInferenceId={} buildingId={}", sourceInferenceId, buildingId);
        } catch (RuntimeException ex) {
            // 综合研判属于视觉成功后的增强链路，失败不得回滚已经成功的 REAL 视觉结果。
            log.warn("REAL 视觉成功后自动综合研判入队失败 sourceInferenceId={} buildingId={}: {}",
                    sourceInferenceId, buildingId, safe(ex));
        }
    }

    private void failOrRetry(AiExecutionTask task, String code, String message, boolean retryable) {
        if (!retryable || task.attemptCount() >= task.maxAttempts()) {
            executionRepository.markFailed(task.id(), code, message);
            return;
        }
        long multiplier = 1L << Math.max(0, task.attemptCount() - 1);
        long delay = Math.min(900L, properties.getRetryBaseDelaySeconds() * multiplier);
        executionRepository.markRetry(task.id(), OffsetDateTime.now().plusSeconds(delay), code, message);
    }

    private static String value(Object raw, String fallback) {
        return raw == null || String.valueOf(raw).isBlank()
                ? fallback : String.valueOf(raw).trim().toUpperCase();
    }

    private static String safe(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
