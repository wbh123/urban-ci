package org.urbansafe.priority.ai.vision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.orchestration.SpringAiLocalVisionOrchestrator;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.provider.DifyWorkflowProvider;

/**
 * 巡检图片统一视觉分析编排器。
 *
 * <p>本地 ACCURACY 始终负责专业 Detection/Polygon；Dify 可用时作为优先的
 * 语义/工作流编排增强。Dify 基础设施或工作流运行故障允许显式回退，回退路径由
 * Spring AI 编排层中的确定性本地视觉子编排器执行，不依赖 DeepSeek 在线。
 */
@Service
public class VisionAnalysisOrchestrator {

    static final String DIFY_IMAGE_WORKFLOW = "DIFY-IMAGE-ANALYSIS-001";
    private static final Set<String> FALLBACK_ERROR_CODES = Set.of(
            AiErrorCodes.AI_PROVIDER_TIMEOUT,
            AiErrorCodes.AI_PROVIDER_UNAVAILABLE,
            AiErrorCodes.AI_PROVIDER_DISABLED,
            AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED,
            AiErrorCodes.AI_WORKFLOW_FAILED);

    private final AiAutomationSettingsService settingsService;
    private final DifyWorkflowProvider difyProvider;
    private final SpringAiLocalVisionOrchestrator localOrchestrator;

    public VisionAnalysisOrchestrator(
            AiAutomationSettingsService settingsService,
            DifyWorkflowProvider difyProvider,
            SpringAiLocalVisionOrchestrator localOrchestrator) {
        this.settingsService = settingsService;
        this.difyProvider = difyProvider;
        this.localOrchestrator = localOrchestrator;
    }

    public VisionAnalysisOutcome analyze(VisionAnalysisRequest request) {
        AiInferenceResponse local = localOrchestrator.analyze(request);
        if (!settingsService.intelligentWorkflowEnabled()) {
            return localOnly(local, "FAST_API", false, null);
        }
        if (!difyProvider.enabled() || !difyProvider.configured()) {
            return localOnly(local, "DIFY", true, AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED);
        }

        try {
            AiStructuredResult dify = difyProvider.execute(buildDifyRequest(request, local));
            return new VisionAnalysisOutcome(
                    local,
                    "DIFY",
                    "DIFY",
                    "DIFY_PREFERRED",
                    false,
                    null,
                    dify.summary(),
                    dify.warnings());
        } catch (AiProviderException ex) {
            if (!FALLBACK_ERROR_CODES.contains(ex.getErrorCode())) {
                throw ex;
            }
            return localOnly(local, "DIFY", true, ex.getErrorCode());
        }
    }

    private AiOrchestrationRequest buildDifyRequest(
            VisionAnalysisRequest request,
            AiInferenceResponse local) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("assetId", request.assetId());
        inputs.put("triggerType", request.triggerType());
        inputs.put("localModelId", local.model() == null ? request.modelId() : local.model().modelId());
        if (local.model() != null && local.model().version() != null) {
            inputs.put("localModelVersion", local.model().version());
        }
        int detectionCount = local.summary() == null
                ? (local.detections() == null ? 0 : local.detections().size())
                : local.summary().detectionCount();
        inputs.put("localDetectionCount", detectionCount);
        if (local.summary() != null && local.summary().classCounts() != null) {
            inputs.put("localClassCounts", local.summary().classCounts());
        }
        return new AiOrchestrationRequest(
                request.requestCode(),
                AiCapabilityType.WORKFLOW,
                "DIFY",
                DIFY_IMAGE_WORKFLOW,
                "REAL",
                request.imageBytes(),
                request.contentType(),
                "结合本地高精度视觉结果整理巡检图片的可见病害、复核重点和补拍建议。",
                inputs);
    }

    private static VisionAnalysisOutcome localOnly(
            AiInferenceResponse response,
            String preferredProvider,
            boolean fallback,
            String fallbackReason) {
        return new VisionAnalysisOutcome(
                response,
                preferredProvider,
                "FAST_API",
                "SPRING_AI_LOCAL",
                fallback,
                fallbackReason,
                null,
                List.of());
    }
}
