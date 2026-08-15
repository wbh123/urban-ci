package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiStructuredResultPrecisionDiagnosticsTest {

    @Test
    void shouldSerializeOptionalDetectionTrustDiagnostics() throws Exception {
        AiStructuredResult.Detection detection = new AiStructuredResult.Detection(
                "CRACK",
                "疑似裂缝",
                0.41,
                new AiStructuredResult.BoundingBox(0.1, 0.2, 0.3, 0.4, "NORMALIZED_XYWH"),
                null,
                "HIGH",
                List.of("MULTI_PROMPT_CONFIRMED", "CROSS_SCALE_CONFIRMED"),
                Map.of("bboxAreaRatio", 0.12));
        AiStructuredResult result = new AiStructuredResult(
                "r1", "FAST_API", "AI-VISION-LOCAL-001", "1.1.0",
                AiCapabilityType.VISION_INFERENCE, "SUCCEEDED", "ok",
                List.of(detection), List.of(), List.of(), 0.41,
                List.of(), "fast-api:r1", 4200L);

        JsonNode json = new ObjectMapper().valueToTree(result);
        JsonNode item = json.path("detections").get(0);
        assertEquals("HIGH", item.path("trustLevel").asText());
        assertEquals("MULTI_PROMPT_CONFIRMED", item.path("trustReasons").get(0).asText());
        assertEquals(0.12, item.path("diagnostics").path("bboxAreaRatio").asDouble(), 1e-9);
    }

    @Test
    void legacyFiveArgumentDetectionConstructorShouldRemainValid() {
        AiStructuredResult.Detection detection = new AiStructuredResult.Detection(
                "CRACK", "疑似裂缝", 0.4,
                new AiStructuredResult.BoundingBox(0.1, 0.1, 0.2, 0.2, "NORMALIZED_XYWH"),
                null);
        assertEquals(List.of(), detection.trustReasons());
        assertEquals(Map.of(), detection.diagnostics());
    }
}
