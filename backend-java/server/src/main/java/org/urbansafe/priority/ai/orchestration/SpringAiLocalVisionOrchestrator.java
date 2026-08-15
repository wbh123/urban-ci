package org.urbansafe.priority.ai.orchestration;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.provider.FastApiAiInferenceProvider;
import org.urbansafe.priority.ai.vision.VisionAnalysisRequest;

/**
 * Spring AI 编排层中的确定性本地视觉子编排器。
 *
 * <p>该路径不依赖 ChatClient/DeepSeek：即使外网或文本模型不可用，也必须能够调用
 * 已批准的 FastAPI ACCURACY 专业视觉底座，并保留完整 Detection、SAM Polygon、trust
 * 与 diagnostics。DeepSeek 只属于可选的上层文本增强，而不是视觉识别强依赖。
 */
@Service
public class SpringAiLocalVisionOrchestrator {

    private final FastApiAiInferenceProvider localProvider;

    public SpringAiLocalVisionOrchestrator(FastApiAiInferenceProvider localProvider) {
        this.localProvider = localProvider;
    }

    public AiInferenceResponse analyze(VisionAnalysisRequest request) {
        localProvider.requireModelReady(request.modelId(), "REAL");
        Map<String, Object> metadata = localProvider.buildMetadata(
                request.requestCode(),
                "REAL",
                request.assetId(),
                request.filename(),
                request.contentType(),
                null,
                request.modelId());
        metadata.put("inferenceProfile", "ACCURACY");
        metadata.put("triggerType", request.triggerType());
        metadata.put("orchestrator", "SPRING_AI_LOCAL");
        return localProvider.infer(request.imageBytes(), metadata, request.requestCode());
    }
}
