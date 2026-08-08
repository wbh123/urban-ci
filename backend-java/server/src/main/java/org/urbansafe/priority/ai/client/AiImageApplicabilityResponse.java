package org.urbansafe.priority.ai.client;

import java.util.Map;

/** FastAPI 本地图片语义适用性门禁响应。 */
public record AiImageApplicabilityResponse(
        String requestId,
        String modelId,
        String modelVersion,
        String status,
        String decision,
        Double confidence,
        Map<String, Double> scores,
        Boolean allowDify,
        String reason) {

    public AiImageApplicabilityResponse {
        scores = scores == null ? Map.of() : Map.copyOf(scores);
    }
}
