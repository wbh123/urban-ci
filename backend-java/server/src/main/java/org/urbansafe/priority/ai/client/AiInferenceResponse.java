package org.urbansafe.priority.ai.client;

import java.util.List;
import java.util.Map;

/**
 * FastAPI 标准推理响应，与 ai-service-python/app/schemas.py 的 InferenceResponse 对齐。
 *
 * <p>该记录只用于 Spring Boot 与 FastAPI 之间的内部契约，不作为 OpenAPI DTO，
 * 也不向 Controller 直接暴露。
 */
public record AiInferenceResponse(
        String requestId,
        String status,
        String mode,
        ModelBrief model,
        ImageInfo image,
        List<Detection> detections,
        Summary summary,
        long durationMs,
        List<String> warnings) {

    /** 模型简要信息。 */
    public record ModelBrief(String modelId, String modelName, String version) {
    }

    /** 图片信息与质量、适用性判定。 */
    public record ImageInfo(int width, int height, String qualityStatus, String applicability) {
    }

    /** 单个检测对象。 */
    public record Detection(
            int sequence,
            String classCode,
            String className,
            double confidence,
            BoundingBox boundingBox) {
    }

    /** 归一化左上角宽高检测框。 */
    public record BoundingBox(double x, double y, double width, double height, String coordinateType) {
    }

    /** 检测汇总。 */
    public record Summary(int detectionCount, Map<String, Integer> classCounts) {
    }
}
