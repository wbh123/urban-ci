package org.urbansafe.priority.ai.vision;

/** 统一视觉分析编排请求；不携带密钥或模型本地路径。 */
public record VisionAnalysisRequest(
        String requestCode,
        String modelId,
        String assetId,
        String filename,
        String contentType,
        byte[] imageBytes,
        String triggerType) {

    public VisionAnalysisRequest {
        imageBytes = imageBytes == null ? new byte[0] : imageBytes.clone();
        triggerType = triggerType == null || triggerType.isBlank()
                ? "MANUAL_SINGLE" : triggerType.trim().toUpperCase();
    }

    @Override
    public byte[] imageBytes() {
        return imageBytes.clone();
    }
}
