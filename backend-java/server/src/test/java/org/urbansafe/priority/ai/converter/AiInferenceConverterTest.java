package org.urbansafe.priority.ai.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiInferenceConverterTest {

    @Test
    void prefersSqlDetectionCountWhenStructuredResultIsUnavailable() {
        Map<String, Object> listRow = new LinkedHashMap<>();
        listRow.put("detectionCount", 3L);

        assertThat(AiInferenceConverter.detectionCount(listRow)).isEqualTo(3);
    }

    @Test
    void usesStructuredDetectionsForHistoricalProjectionMismatch() {
        Map<String, Object> detailRow = new LinkedHashMap<>();
        detailRow.put("detections", List.of());
        detailRow.put("structuredResult", Map.of(
                "detections", List.of(Map.of("classCode", "CRACK"), Map.of("classCode", "CRACK"))));
        detailRow.put("summary", Map.of("detectionCount", 2));

        assertThat(AiInferenceConverter.detectionCount(detailRow)).isEqualTo(2);
        assertThat(AiInferenceConverter.detectionConsistency(detailRow)).isEqualTo("MISMATCH");
    }

    @Test
    void usesPersistedDetailDetectionsWhenStructuredResultIsUnavailable() {
        Map<String, Object> detailRow = new LinkedHashMap<>();
        detailRow.put("detections", List.of(Map.of(), Map.of()));

        assertThat(AiInferenceConverter.detectionCount(detailRow)).isEqualTo(2);
    }

    @Test
    void fallsBackToStructuredResultDetections() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("structuredResult", Map.of("detections", List.of(Map.of(), Map.of(), Map.of())));

        assertThat(AiInferenceConverter.detectionCount(row)).isEqualTo(3);
    }

    @Test
    void reportsConsistentWhenAvailableCountsAgree() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("detections", List.of(Map.of(), Map.of()));
        row.put("structuredResult", Map.of("detections", List.of(Map.of(), Map.of())));
        row.put("summary", Map.of("detectionCount", 2));

        assertThat(AiInferenceConverter.detectionCount(row)).isEqualTo(2);
        assertThat(AiInferenceConverter.detectionConsistency(row)).isEqualTo("CONSISTENT");
    }

    @Test
    void returnsZeroWhenNoDetectionSourcePresent() {
        assertThat(AiInferenceConverter.detectionCount(Map.of())).isZero();
    }
}
