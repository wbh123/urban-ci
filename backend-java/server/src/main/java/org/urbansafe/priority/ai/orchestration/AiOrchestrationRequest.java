package org.urbansafe.priority.ai.orchestration;

import java.util.Map;

/**
 * 统一人工智能编排请求。
 *
 * <p>业务层只提交完成当前任务所需的最小数据，不携带密钥、模型路径或运行环境信息。
 */
public record AiOrchestrationRequest(
        String requestId,
        AiCapabilityType capabilityType,
        String requestedProviderCode,
        String modelCode,
        String taskMode,
        byte[] imageBytes,
        String contentType,
        String prompt,
        Map<String, Object> inputs) {

    public AiOrchestrationRequest {
        imageBytes = imageBytes == null ? null : imageBytes.clone();
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }

    @Override
    public byte[] imageBytes() {
        return imageBytes == null ? null : imageBytes.clone();
    }
}
