package org.urbansafe.priority.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiIntelligentAnalysisControllerTest {

    @Test
    void keepsOnlyBusinessSafeContextAndAddsBuildingIdForInferenceReview() {
        UUID buildingId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID sourceInferenceId = UUID.randomUUID();

        Map<String, Object> context = AiIntelligentAnalysisController.safeContext(
                "AI_INFERENCE",
                Map.of(
                        "assetId", assetId.toString(),
                        "sourceInferenceId", sourceInferenceId.toString(),
                        "riskLevel", "HIGH",
                        "provider", "SHOULD_NOT_PASS",
                        "systemPrompt", "SHOULD_NOT_PASS"),
                buildingId);

        assertThat(context)
                .containsEntry("assetId", assetId.toString())
                .containsEntry("sourceInferenceId", sourceInferenceId.toString())
                .containsEntry("buildingId", buildingId.toString())
                .containsEntry("riskLevel", "HIGH")
                .doesNotContainKeys("provider", "systemPrompt");
    }

    @Test
    void usesVersionedSourceInferenceIdAsStableAutomaticAnalysisIdempotencyKey() {
        UUID sourceInferenceId = UUID.randomUUID();
        assertThat(AiIntelligentAnalysisController.analysisIdempotencyKey(
                Map.of("sourceInferenceId", sourceInferenceId.toString())))
                .isEqualTo("intelligent-analysis:source-bound-v2:source-inference:" + sourceInferenceId);
    }

    @Test
    void manualAnalysisKeepsFreshIdempotencyKey() {
        String first = AiIntelligentAnalysisController.analysisIdempotencyKey(Map.of());
        String second = AiIntelligentAnalysisController.analysisIdempotencyKey(Map.of());
        assertThat(first).startsWith("intelligent-analysis:");
        assertThat(second).startsWith("intelligent-analysis:");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void doesNotAliasArbitraryBusinessIdAsBuildingId() {
        UUID reportId = UUID.randomUUID();
        Map<String, Object> context = AiIntelligentAnalysisController.safeContext(
                "FEEDBACK", Map.of(), reportId);

        assertThat(context).doesNotContainKey("buildingId");
    }
}
