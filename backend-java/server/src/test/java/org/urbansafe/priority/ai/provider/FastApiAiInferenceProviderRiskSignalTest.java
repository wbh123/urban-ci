package org.urbansafe.priority.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;

class FastApiAiInferenceProviderRiskSignalTest {

    @Test
    void shouldConvertDetectionsToDeduplicatedVisualRiskSignals() {
        List<AiInferenceResponse.Detection> detections = List.of(
                detection(1, "CRACK", "疑似裂缝", 0.42),
                detection(2, "CRACK", "疑似裂缝", 0.78),
                detection(3, "WATER_STAIN", "疑似水渍", 0.31));

        List<AiOrchestrationResult.RiskSignal> signals =
                FastApiAiInferenceProvider.toVisualRiskSignals(detections);

        assertEquals(2, signals.size());
        assertEquals("VISUAL_CRACK", signals.get(0).code());
        assertEquals("HIGH", signals.get(0).level());
        assertEquals(0.78, signals.get(0).confidence());
        assertTrue(signals.get(0).description().contains("疑似裂缝"));
        assertTrue(signals.get(0).description().contains("不代表正式风险等级"));
        assertEquals("VISUAL_WATER_STAIN", signals.get(1).code());
        assertEquals("LOW", signals.get(1).level());
    }

    @Test
    void precisionTrustLevelShouldOverrideRawConfidenceHeuristic() {
        AiInferenceResponse.Detection highRawButLowTrust = new AiInferenceResponse.Detection(
                1,
                "CRACK",
                "疑似裂缝",
                0.91,
                new AiInferenceResponse.BoundingBox(0.1, 0.1, 0.2, 0.2, "NORMALIZED_XYWH"),
                null,
                "LOW",
                List.of("MASK_SHAPE_SUSPICIOUS"),
                Map.of("bboxAreaRatio", 0.04));

        List<AiOrchestrationResult.RiskSignal> signals =
                FastApiAiInferenceProvider.toVisualRiskSignals(List.of(highRawButLowTrust));

        assertEquals(1, signals.size());
        assertEquals("LOW", signals.get(0).level());
        assertEquals(0.91, signals.get(0).confidence());
        assertTrue(signals.get(0).description().contains("模型候选可信度 LOW"));
    }

    @Test
    void shouldReturnEmptySignalsWhenNoDetections() {
        assertTrue(FastApiAiInferenceProvider.toVisualRiskSignals(List.of()).isEmpty());
    }

    @Test
    void shouldMapEverySupportedClassCodeToVisualRiskCode() {
        List<AiInferenceResponse.Detection> detections = List.of(
                detection(1, "SPALLING", "疑似剥落", 0.5),
                detection(2, "EXPOSED_REBAR", "疑似露筋", 0.5),
                detection(3, "CORROSION", "疑似锈蚀", 0.5),
                detection(4, "SURFACE_DAMAGE", "疑似表面损伤", 0.5));
        List<AiOrchestrationResult.RiskSignal> signals =
                FastApiAiInferenceProvider.toVisualRiskSignals(detections);
        assertEquals(4, signals.size());
        assertTrue(signals.stream().anyMatch(s -> "VISUAL_SPALLING".equals(s.code())));
        assertTrue(signals.stream().anyMatch(s -> "VISUAL_EXPOSED_REBAR".equals(s.code())));
        assertTrue(signals.stream().anyMatch(s -> "VISUAL_CORROSION".equals(s.code())));
        assertTrue(signals.stream().anyMatch(s -> "VISUAL_SURFACE_DAMAGE".equals(s.code())));
    }

    private static AiInferenceResponse.Detection detection(
            int sequence, String code, String name, double confidence) {
        return new AiInferenceResponse.Detection(
                sequence,
                code,
                name,
                confidence,
                new AiInferenceResponse.BoundingBox(0.1, 0.1, 0.2, 0.2, "NORMALIZED_XYWH"),
                null);
    }
}
