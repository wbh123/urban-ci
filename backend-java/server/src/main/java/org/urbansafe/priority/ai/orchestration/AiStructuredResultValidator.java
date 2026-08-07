package org.urbansafe.priority.ai.orchestration;

import org.urbansafe.priority.ai.provider.AiProviderException;

/** 对统一人工智能输出执行必要字段和取值范围校验。 */
public class AiStructuredResultValidator {

    public void validate(AiOrchestrationRequest request, AiStructuredResult result) {
        if (result == null) {
            invalid("人工智能提供者未返回结构化结果");
        }
        if (blank(result.requestId()) || blank(result.providerCode())
                || blank(result.modelCode()) || result.capabilityType() == null
                || blank(result.status()) || blank(result.summary())) {
            invalid("人工智能结构化结果缺少必要字段");
        }
        if (request != null && request.requestId() != null
                && !request.requestId().equals(result.requestId())) {
            invalid("人工智能结果请求编号不一致");
        }
        if (request != null && request.capabilityType() != result.capabilityType()) {
            invalid("人工智能结果能力类型不一致");
        }
        if (!"SUCCEEDED".equals(result.status()) && !"REJECTED".equals(result.status())) {
            invalid("人工智能结果状态无效");
        }
        if (result.confidence() != null
                && (result.confidence() < 0d || result.confidence() > 1d)) {
            invalid("人工智能结果置信度必须位于零到一之间");
        }
        if (result.durationMs() < 0L) {
            invalid("人工智能结果耗时不能为负数");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void invalid(String message) {
        throw new AiProviderException(AiErrorCodes.AI_OUTPUT_VALIDATION_FAILED, message);
    }
}
