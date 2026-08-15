package org.urbansafe.priority.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FastApiAiInferenceProviderPrecisionProfileTest {

    @Test
    void shouldNormalizeAndPassPrecisionProfile() {
        Map<String, Object> metadata = new LinkedHashMap<>();

        FastApiAiInferenceProvider.applyInferenceProfile(
                metadata, Map.of("inferenceProfile", "precision"));

        assertEquals("PRECISION", metadata.get("inferenceProfile"));
    }

    @Test
    void shouldLeaveMetadataUntouchedWhenProfileIsAbsent() {
        Map<String, Object> metadata = new LinkedHashMap<>();

        FastApiAiInferenceProvider.applyInferenceProfile(metadata, Map.of());

        assertEquals(Map.of(), metadata);
    }

    @Test
    void shouldRejectUnknownInferenceProfile() {
        Map<String, Object> metadata = new LinkedHashMap<>();

        AiProviderException error = assertThrows(
                AiProviderException.class,
                () -> FastApiAiInferenceProvider.applyInferenceProfile(
                        metadata, Map.of("inferenceProfile", "turbo")));

        assertEquals("AI_REQUEST_INVALID", error.getErrorCode());
    }
}
