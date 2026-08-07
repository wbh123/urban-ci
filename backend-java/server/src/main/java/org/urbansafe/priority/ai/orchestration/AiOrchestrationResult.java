package org.urbansafe.priority.ai.orchestration;

import java.util.List;

/** 明确表示结果来自第七阶段统一编排链路。 */
public final class AiOrchestrationResult extends AiStructuredResult {

    public AiOrchestrationResult(
            String requestId,
            String providerCode,
            String modelCode,
            String modelVersion,
            AiCapabilityType capabilityType,
            String status,
            String summary,
            List<Detection> detections,
            List<RiskSignal> riskSignals,
            List<String> recommendations,
            Double confidence,
            List<String> warnings,
            String rawResponseReference,
            long durationMs) {
        super(requestId, providerCode, modelCode, modelVersion, capabilityType, status, summary,
                detections, riskSignals, recommendations, confidence, warnings,
                rawResponseReference, durationMs);
    }
}
