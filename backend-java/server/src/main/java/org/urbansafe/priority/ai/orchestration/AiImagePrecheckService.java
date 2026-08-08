package org.urbansafe.priority.ai.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiImageQualityResponse;
import org.urbansafe.priority.ai.provider.AiProviderException;

/**
 * Dify 图片分析前置质量门禁。
 *
 * <p>仅对固定图片分析工作流执行无需模型权重的本地质量预检；其他能力请求原样通过。
 */
@Service
public class AiImagePrecheckService {

    static final String IMAGE_ANALYSIS_WORKFLOW = "DIFY-IMAGE-ANALYSIS-001";

    private final AiFastApiClient fastApiClient;
    private final ObjectMapper objectMapper;

    public AiImagePrecheckService(AiFastApiClient fastApiClient, ObjectMapper objectMapper) {
        this.fastApiClient = fastApiClient;
        this.objectMapper = objectMapper;
    }

    /** 低质量图片抛出稳定拒绝错误；合格图片把结构化预检结果写入 precheckJson。 */
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

        String precheckJson;
        try {
            precheckJson = objectMapper.writeValueAsString(quality);
        } catch (JsonProcessingException ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE,
                    "图片质量预检结果序列化失败",
                    ex);
        }

        Map<String, Object> inputs = new LinkedHashMap<>(request.inputs());
        inputs.put("precheckJson", precheckJson);
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

    private boolean requiresImagePrecheck(AiOrchestrationRequest request) {
        return request != null
                && IMAGE_ANALYSIS_WORKFLOW.equals(request.modelCode())
                && request.imageBytes() != null
                && request.imageBytes().length > 0;
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
