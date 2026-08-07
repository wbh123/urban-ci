package org.urbansafe.priority.assessment.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssessmentChecksumServiceTest {

    private final AssessmentInputCanonicalizer canonicalizer =
            new AssessmentInputCanonicalizer(new ObjectMapper());
    private final AssessmentChecksumService service =
            new AssessmentChecksumService(canonicalizer);

    @Test
    void mapAndArrayOrderProduceSameCanonicalChecksum() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", List.of(Map.of("id", 2), Map.of("id", 1)));
        first.put("a", 1);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1.0000001);
        second.put("b", List.of(Map.of("id", 1), Map.of("id", 2)));

        assertThat(service.checksum(first)).isEqualTo(service.checksum(second));
    }

    @Test
    void temporarySecretsAreExcludedFromSnapshot() {
        Map<String, Object> safe = Map.of("assetId", "a-1");
        Map<String, Object> withSecret = Map.of(
                "assetId", "a-1",
                "presignedUrl", "http://temporary",
                "trackingSecret", "secret");

        assertThat(service.checksum(safe)).isEqualTo(service.checksum(withSecret));
    }
}
