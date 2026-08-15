package org.urbansafe.priority.ai.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.client.AiRuntimeModelInfo;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;

/** FastAPI 模型服务适配器。 */
public class FastApiAiInferenceProvider implements AiInferenceProvider, AiCapabilityProvider {

    public static final String PROVIDER_CODE = "FAST_API";

    private static final Map<String, String> RISK_CODES = Map.of(
            "CRACK", "VISUAL_CRACK",
            "SPALLING", "VISUAL_SPALLING",
            "EXPOSED_REBAR", "VISUAL_EXPOSED_REBAR",
            "CORROSION", "VISUAL_CORROSION",
            "WATER_STAIN", "VISUAL_WATER_STAIN",
            "SURFACE_DAMAGE", "VISUAL_SURFACE_DAMAGE");

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
        applyInferenceProfile(metadata, request.inputs());
        AiInferenceResponse response = infer(request.imageBytes(), metadata, request.requestId());
        List<AiOrchestrationResult.Detection> detections = response.detections().stream()
                .map(item -> new AiOrchestrationResult.Detection(
                        item.classCode(), item.className(), item.confidence(),
                        new AiOrchestrationResult.BoundingBox(
                                item.boundingBox().x(), item.boundingBox().y(),
                                item.boundingBox().width(), item.boundingBox().height(),
                                item.boundingBox().coordinateType()),
                        toStructuredSegmentation(item.segmentation()),
                        item.trustLevel(),
                        item.trustReasons(),
                        item.diagnostics()))
                .toList();
        List<AiOrchestrationResult.RiskSignal> riskSignals = toVisualRiskSignals(response.detections());
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
                riskSignals,
                List.of("人工复核后再用于评分和报告"),
                confidence,
                response.warnings(),
                "fast-api:" + response.requestId(),
                response.durationMs());
    }

    /** 可选透传 FAST/PRECISION/ACCURACY；缺省不写入 metadata，由 FastAPI 保持 FAST 默认。 */
    static void applyInferenceProfile(
            Map<String, Object> metadata, Map<String, Object> inputs) {
        Object raw = inputs == null ? null : inputs.get("inferenceProfile");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return;
        }
        String normalized = String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("FAST", "PRECISION", "ACCURACY").contains(normalized)) {
            throw new AiProviderException(
                    "AI_REQUEST_INVALID", "不支持的视觉推理档位，仅允许 FAST、PRECISION 或 ACCURACY");
        }
        metadata.put("inferenceProfile", normalized);
    }

    static List<AiOrchestrationResult.RiskSignal> toVisualRiskSignals(
            List<AiInferenceResponse.Detection> detections) {
        if (detections == null || detections.isEmpty()) {
            return List.of();
        }
        Map<String, AiInferenceResponse.Detection> strongestByClass = new LinkedHashMap<>();
        for (AiInferenceResponse.Detection detection : detections) {
            if (detection == null || detection.classCode() == null) {
                continue;
            }
            String classCode = detection.classCode().trim().toUpperCase(Locale.ROOT);
            if (!RISK_CODES.containsKey(classCode)) {
                continue;
            }
            strongestByClass.merge(classCode, detection,
                    (left, right) -> right.confidence() > left.confidence() ? right : left);
        }
        return strongestByClass.entrySet().stream()
                .map(entry -> {
                    AiInferenceResponse.Detection detection = entry.getValue();
                    double confidence = detection.confidence();
                    String level = visualTrustLevel(detection, confidence);
                    String className = detection.className() == null || detection.className().isBlank()
                            ? entry.getKey()
                            : detection.className();
                    String description = String.format(Locale.ROOT,
                            "视觉模型检测到%s候选，原始置信度 %.0f%%，模型候选可信度 %s；该信号仅用于专业复核，不代表正式风险等级。",
                            className, confidence * 100.0, level);
                    return new AiOrchestrationResult.RiskSignal(
                            RISK_CODES.get(entry.getKey()), level, description, confidence);
                })
                .toList();
    }

    private static String visualTrustLevel(
            AiInferenceResponse.Detection detection, double confidence) {
        String trust = detection.trustLevel();
        if (trust != null) {
            String normalized = trust.trim().toUpperCase(Locale.ROOT);
            if (Set.of("HIGH", "MEDIUM", "LOW").contains(normalized)) {
                return normalized;
            }
        }
        return confidence >= 0.65 ? "HIGH" : confidence >= 0.40 ? "MEDIUM" : "LOW";
    }

    private static String stringInput(AiOrchestrationRequest request, String key) {
        Object value = request.inputs().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static AiOrchestrationResult.Segmentation toStructuredSegmentation(
            AiInferenceResponse.Segmentation segmentation) {
        if (segmentation == null) {
            return null;
        }
        return new AiOrchestrationResult.Segmentation(
                segmentation.type(),
                segmentation.points() == null ? null : List.copyOf(segmentation.points()));
    }
}
