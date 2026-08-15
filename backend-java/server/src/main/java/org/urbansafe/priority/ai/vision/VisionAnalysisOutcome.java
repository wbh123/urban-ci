package org.urbansafe.priority.ai.vision;

import java.util.List;
import org.urbansafe.priority.ai.client.AiInferenceResponse;

/** 统一视觉路由结果；专业 Detection 始终来自本地 ACCURACY。 */
public record VisionAnalysisOutcome(
        AiInferenceResponse response,
        String preferredProvider,
        String actualProvider,
        String orchestrationMode,
        boolean fallback,
        String fallbackReason,
        String difySummary,
        List<String> difyWarnings) {

    public VisionAnalysisOutcome {
        difyWarnings = difyWarnings == null ? List.of() : List.copyOf(difyWarnings);
    }
}
