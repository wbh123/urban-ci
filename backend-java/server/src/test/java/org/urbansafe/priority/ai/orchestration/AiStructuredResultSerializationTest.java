package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 结构化结果必须能序列化为契约 JSON，供 ai.inference_result.structured_result 不可变快照使用。 */
class AiStructuredResultSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesStructuredResultWithDetectionsAndRiskSignals() throws Exception {
        AiStructuredResult result = new AiStructuredResult(
                "req-1", "DIFY", "AI-DIFY-WORKFLOW-001", "image-analysis-v1.1.0",
                AiCapabilityType.WORKFLOW, "SUCCEEDED", "检测到裂缝",
                List.of(new AiStructuredResult.Detection("CRACK", "裂缝", 0.8d, null)),
                List.of(new AiStructuredResult.RiskSignal(
                        "VISIBLE_CRACK", "MEDIUM", "存在可见裂缝，可能影响结构安全", 0.8d)),
                List.of("建议进行人工复核"), 0.8d, List.of("信息限制"), "dify:run-1", 1000L);

        String json = objectMapper.writeValueAsString(result);

        assertTrue(json.contains("\"detections\""), "detections must be serialized");
        assertTrue(json.contains("CRACK"), "detection classCode must be serialized");
        assertTrue(json.contains("\"riskSignals\""), "riskSignals must be serialized");
        assertTrue(json.contains("VISIBLE_CRACK"), "riskSignal code must be serialized");
        assertTrue(json.contains("\"summary\""), "summary must be serialized");
        assertTrue(json.contains("\"warnings\""), "warnings must be serialized");

        Map<?, ?> roundTrip = objectMapper.readValue(json, Map.class);
        assertEquals("检测到裂缝", roundTrip.get("summary"));
        assertEquals("DIFY", roundTrip.get("providerCode"));
    }
}
