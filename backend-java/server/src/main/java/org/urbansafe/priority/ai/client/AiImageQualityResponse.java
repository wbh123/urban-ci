package org.urbansafe.priority.ai.client;

import java.util.List;

/** FastAPI 本地确定性图片质量预检响应。 */
public record AiImageQualityResponse(
        String requestId,
        String modelId,
        String modelVersion,
        String status,
        String decodeStatus,
        String contentType,
        int width,
        int height,
        double brightness,
        double contrast,
        double sharpness,
        boolean blank,
        boolean underexposed,
        boolean overexposed,
        boolean blurDetected,
        boolean lowResolution,
        boolean lowQuality,
        boolean reshootRecommended,
        List<String> reasons) {

    public AiImageQualityResponse {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
