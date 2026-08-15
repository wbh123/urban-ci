package org.urbansafe.priority.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiRichDetectionDetailServiceTest {

    @Test
    void snapshotShouldOverrideStaleRegistryModelAndRestoreDetections() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("modelId", "AI-VISION-LOCAL-001");
        source.put("modelName", "UrbanSafe Grounded SAM2 Tiny 零样本建筑表观病害");
        source.put("modelVersion", "1.0.0");

        String raw = """
                {
                  "model": {
                    "modelId": "AI-VISION-LOCAL-001",
                    "modelName": "UrbanSafe Grounded SAM2 Base 零样本建筑表观病害",
                    "version": "1.1.0"
                  },
                  "detections": [{
                    "sequence": 1,
                    "classCode": "CRACK",
                    "className": "裂缝",
                    "confidence": 0.91,
                    "boundingBox": {"x":0.1,"y":0.2,"width":0.3,"height":0.4,"coordinateType":"NORMALIZED_XYWH"},
                    "segmentation": {"type":"POLYGON","points":[[0.1,0.2],[0.4,0.2],[0.4,0.6]]}
                  }]
                }
                """;

        Map<String, Object> result = AiRichDetectionDetailService.enrichSnapshot(
                source, raw, new ObjectMapper());

        assertThat(result.get("modelVersion")).isEqualTo("1.1.0");
        assertThat(result.get("modelName")).isEqualTo("UrbanSafe Grounded SAM2 Base 零样本建筑表观病害");
        assertThat(result.get("detectionCount")).isEqualTo(1);
    }
}
