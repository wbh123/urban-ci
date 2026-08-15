package org.urbansafe.priority.ai.tools;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationService;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.security.AiVisionAssetAccessService;
import org.urbansafe.priority.asset.service.Phase2AssetService;

/** Spring AI 本地视觉 Tool：分析指定图片资产中的疑似建筑病害。 */
@Component
public class VisionAnalysisTool {

    private static final String MODEL_ID = "AI-VISION-LOCAL-001";
    private static final String PRECISION_PROFILE = "PRECISION";
    private static final Set<String> SUPPORTED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final String DEFAULT_CONTENT_TYPE = "image/jpeg";

    private final Phase2AssetService assetService;
    private final AiVisionAssetAccessService assetAccessService;
    private final AiOrchestrationService orchestrationService;

    public VisionAnalysisTool(
            Phase2AssetService assetService,
            AiVisionAssetAccessService assetAccessService,
            AiOrchestrationService orchestrationService) {
        this.assetService = assetService;
        this.assetAccessService = assetAccessService;
        this.orchestrationService = orchestrationService;
    }

    @Tool(description = """
            分析指定巡检图片资产（assetId）中的疑似建筑病害。
            调用时必须同时提供该图片所属楼栋的 buildingId；系统会在服务端校验
            assetId 确实绑定到该楼栋且当前用户有权读取该楼栋后才允许分析。
            当需要了解图片中的裂缝、剥落、露筋、锈蚀、水渍等视觉信息，或需要
            实时视觉分析时使用。专业复核默认采用精度优先多尺度模式，允许更长推理时间；
            结果仅用于辅助判断，不构成专业确认结论。
            """)
    public VisionToolResult analyze(String assetId, String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("VisionAnalysisTool", "FAST_API");
        try {
            UUID id = UUID.fromString(assetId);
            UUID requestedBuildingId = UUID.fromString(buildingId);
            assetAccessService.assertCanReadAssetForBuilding(id, requestedBuildingId);
            Map<String, Object> assetMeta = assetService.get(id);
            byte[] imageBytes = assetService.content(id);
            String contentType = normalizeContentType(
                    String.valueOf(assetMeta.getOrDefault("contentType", DEFAULT_CONTENT_TYPE)));
            AiOrchestrationRequest request = new AiOrchestrationRequest(
                    UUID.randomUUID().toString(),
                    AiCapabilityType.VISION_INFERENCE,
                    "FAST_API",
                    MODEL_ID,
                    "REAL",
                    imageBytes,
                    contentType,
                    "分析图片中的疑似建筑病害",
                    Map.of(
                            "buildingId", requestedBuildingId.toString(),
                            "assetId", id.toString(),
                            "inferenceProfile", PRECISION_PROFILE));
            AiStructuredResult result = orchestrationService.execute(request);
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return toResult(result);
        } catch (AiProviderException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getErrorCode(), ex.getMessage());
            return new VisionToolResult(
                    MODEL_ID, "FAILED", 0, List.of(), 0L,
                    "本次未获得实时视觉分析结果：" + ex.getMessage());
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    private static VisionToolResult toResult(AiStructuredResult result) {
        int detectionCount = result.detections().size();
        List<VisionDetection> detections = result.detections().stream()
                .limit(10)
                .map(d -> new VisionDetection(
                        d.classCode(), d.className(), d.confidence(),
                        d.boundingBox() == null
                                ? null
                                : new double[] {
                                    d.boundingBox().x(), d.boundingBox().y(),
                                    d.boundingBox().width(), d.boundingBox().height()},
                        d.segmentation() == null ? null : d.segmentation().type()))
                .toList();
        return new VisionToolResult(
                result.modelCode(),
                result.status(),
                detectionCount,
                detections,
                result.durationMs(),
                "视觉结果为疑似病害，仅辅助筛查，需人工复核");
    }

    public record VisionToolResult(
            String modelCode,
            String status,
            int detectionCount,
            List<VisionDetection> detections,
            long durationMs,
            String disclaimer) {
    }

    public record VisionDetection(
            String classCode,
            String className,
            Double confidence,
            double[] boundingBox,
            String segmentationType) {
    }

    /** 标准化图片 Content-Type，仅允许 JPEG/PNG/WebP，其余回退默认值。 */
    static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        return SUPPORTED_CONTENT_TYPES.contains(normalized) ? normalized : DEFAULT_CONTENT_TYPE;
    }
}
