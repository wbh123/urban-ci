package org.urbansafe.priority.ai.provider;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.client.AiRuntimeModelInfo;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;

/**
 * FastAPI 模型服务适配器。
 *
 * <p>同时保持第六阶段图片推理接口与第七阶段通用能力接口，业务层仍不感知模型运行环境。
 */
public class FastApiAiInferenceProvider implements AiInferenceProvider, AiCapabilityProvider {

    public static final String PROVIDER_CODE = "FAST_API";

    private final AiFastApiClient client;

    public FastApiAiInferenceProvider(AiFastApiClient client) {
        this.client = client;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public Set<AiCapabilityType> capabilities() {
        return Set.of(AiCapabilityType.VISION_INFERENCE);
    }

    @Override
    public AiRuntimeModelInfo requireModelReady(String expectedModelId, String expectedMode) {
        return client.requireModelReady(expectedModelId, expectedMode);
    }

    @Override
    public AiInferenceResponse infer(
            byte[] imageBytes, Map<String, Object> metadata, String requestId) {
        return client.infer(imageBytes, metadata, requestId);
    }

    @Override
    public Map<String, Object> buildMetadata(
            String requestId,
            String mode,
            String assetId,
            String filename,
            String contentType,
            String sha256,
            String requestedModelId) {
        return client.buildMetadata(
                requestId, mode, assetId, filename, contentType, sha256, requestedModelId);
    }

    @Override
    public AiOrchestrationResult execute(AiOrchestrationRequest request) {
        if ("REAL".equalsIgnoreCase(request.taskMode())) {
            requireModelReady(request.modelCode(), request.taskMode());
        }
        Map<String, Object> metadata = buildMetadata(
                request.requestId(), request.taskMode(),
                stringInput(request, "assetId"), "inspection-image",
                request.contentType(), null, request.modelCode());
        AiInferenceResponse response = infer(request.imageBytes(), metadata, request.requestId());
        List<AiOrchestrationResult.Detection> detections = response.detections().stream()
                .map(item -> new AiOrchestrationResult.Detection(
                        item.classCode(), item.className(), item.confidence(),
                        new AiOrchestrationResult.BoundingBox(
                                item.boundingBox().x(), item.boundingBox().y(),
                                item.boundingBox().width(), item.boundingBox().height(),
                                item.boundingBox().coordinateType())))
                .toList();
        Double confidence = response.detections().stream()
                .map(AiInferenceResponse.Detection::confidence)
                .max(Double::compareTo)
                .orElse(null);
        String summary = response.summary() == null
                ? "模型分析完成"
                : "模型分析完成，共发现 " + response.summary().detectionCount() + " 个候选目标";
        return new AiOrchestrationResult(
                response.requestId(),
                PROVIDER_CODE,
                response.model().modelId(),
                response.model().version(),
                AiCapabilityType.VISION_INFERENCE,
                response.status(),
                summary,
                detections,
                List.of(),
                List.of("人工复核后再用于评分和报告"),
                confidence,
                response.warnings(),
                "fast-api:" + response.requestId(),
                response.durationMs());
    }

    private static String stringInput(AiOrchestrationRequest request, String key) {
        Object value = request.inputs().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
