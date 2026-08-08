package org.urbansafe.priority.ai.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiImageApplicabilityClient;
import org.urbansafe.priority.ai.client.AiImageApplicabilityResponse;
import org.urbansafe.priority.ai.client.AiImageQualityResponse;
import org.urbansafe.priority.ai.provider.AiProviderException;

/**
 * Dify 图片分析前置门禁。
 *
 * <p>固定顺序执行本地图片质量预检和语义适用性预检：质量不合格直接拒绝；
 * 质量合格后只有高置信 NOT_APPLICABLE 才在本地拒绝，APPLICABLE/UNCERTAIN 均继续 Dify。
 * 业务推理链路可能传入模型登记码，也可能由工作流入口传入工作流码，因此两种标识都必须识别。
 */
@Service
public class AiImagePrecheckService {

    static final String IMAGE_ANALYSIS_WORKFLOW = "DIFY-IMAGE-ANALYSIS-001";
    static final String IMAGE_ANALYSIS_MODEL = "AI-DIFY-WORKFLOW-001";

    private final AiFastApiClient fastApiClient;
    private final AiImageApplicabilityClient applicabilityClient;
    private final ObjectMapper objectMapper;

    public AiImagePrecheckService(
            AiFastApiClient fastApiClient,
            AiImageApplicabilityClient applicabilityClient,
            ObjectMapper objectMapper) {
        this.fastApiClient = fastApiClient;
        this.applicabilityClient = applicabilityClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 低质量或高置信不适用图片抛出稳定业务拒绝；其余图片把两级结果合并进既有 precheckJson。
     *
     * <p>沿用既有 Dify start variable `precheckJson`，避免在未重新导入 DSL 前发送未声明 input。
     * 质量字段保持顶层兼容，语义结果位于 `applicabilityPrecheck` 子对象。
     */
    public AiOrchestrationRequest precheck(AiOrchestrationRequest request) {
        if (!requiresImagePrecheck(request)) {
            return request;
        }

        AiImageQualityResponse quality;
        try {
            quality = fastApiClient.analyzeImageQuality(request.imageBytes(), request.requestId());
        } catch (AiFastApiException ex) {
            throw mapFastApiError(ex);
        }

        if (quality.lowQuality()) {
            String reasons = quality.reasons().isEmpty()
                    ? "图片质量不足，建议补拍"
                    : String.join(",", quality.reasons());
            throw new AiProviderException(
                    AiErrorCodes.AI_IMAGE_LOW_QUALITY,
                    "图片质量预检未通过：" + reasons);
        }

        AiImageApplicabilityResponse applicability;
        try {
            applicability = applicabilityClient.analyze(request.imageBytes(), request.requestId());
        } catch (AiFastApiException ex) {
            throw mapFastApiError(ex);
        }

        if ("NOT_APPLICABLE".equals(applicability.decision())
                && Boolean.FALSE.equals(applicability.allowDify())) {
            throw new AiProviderException(
                    AiErrorCodes.AI_IMAGE_NOT_APPLICABLE,
                    "本地语义门禁判定图片不适用于建筑表观病害分析："
                            + applicability.reason()
                            + "，confidence="
                            + applicability.confidence());
        }

        Map<String, Object> inputs = new LinkedHashMap<>(request.inputs());
        inputs.put("precheckJson", serializeCombinedPrecheck(quality, applicability));
        return new AiOrchestrationRequest(
                request.requestId(),
                request.capabilityType(),
                request.requestedProviderCode(),
                request.modelCode(),
                request.taskMode(),
                request.imageBytes(),
                request.contentType(),
                request.prompt(),
                inputs);
    }

    private String serializeCombinedPrecheck(
            AiImageQualityResponse quality,
            AiImageApplicabilityResponse applicability) {
        try {
            ObjectNode payload = objectMapper.valueToTree(quality);
            payload.set("applicabilityPrecheck", objectMapper.valueToTree(applicability));
            return objectMapper.writeValueAsString(payload);
        } catch (IllegalArgumentException | JsonProcessingException ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE,
                    "本地图片预检结果序列化失败",
                    ex);
        }
    }

    private boolean requiresImagePrecheck(AiOrchestrationRequest request) {
        if (request == null || request.imageBytes() == null || request.imageBytes().length == 0) {
            return false;
        }
        String modelOrWorkflowCode = request.modelCode();
        return IMAGE_ANALYSIS_WORKFLOW.equals(modelOrWorkflowCode)
                || IMAGE_ANALYSIS_MODEL.equals(modelOrWorkflowCode);
    }

    private AiProviderException mapFastApiError(AiFastApiException ex) {
        String code = ex.getErrorCode();
        if ("AI_SERVICE_TIMEOUT".equals(code)) {
            return new AiProviderException(AiErrorCodes.AI_PROVIDER_TIMEOUT, ex.getMessage(), ex);
        }
        if ("AI_SERVICE_UNAVAILABLE".equals(code)) {
            return new AiProviderException(AiErrorCodes.AI_PROVIDER_UNAVAILABLE, ex.getMessage(), ex);
        }
        if ("AI_SERVICE_INVALID_RESPONSE".equals(code)) {
            return new AiProviderException(AiErrorCodes.AI_INVALID_RESPONSE, ex.getMessage(), ex);
        }
        return new AiProviderException(code, ex.getMessage(), ex);
    }
}
