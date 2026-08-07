package org.urbansafe.priority.ai.client;

import java.util.List;

/**
 * FastAPI 推理错误响应，与 ai-service-python/app/schemas.py 的 InferenceErrorDetail 对齐。
 */
public record AiInferenceErrorDetail(
        String requestId,
        String status,
        String errorCode,
        String errorMessage,
        String mode,
        AiInferenceResponse.ModelBrief model,
        List<String> warnings) {
}
