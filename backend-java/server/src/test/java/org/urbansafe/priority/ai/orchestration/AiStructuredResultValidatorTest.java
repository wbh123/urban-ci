package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.provider.AiProviderException;

class AiStructuredResultValidatorTest {

    @Test
    void rejectsMissingSummary() {
        AiStructuredResult invalid = result(" ", 0.5d);

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> new AiStructuredResultValidator().validate(request(), invalid));

        assertEquals(AiErrorCodes.AI_OUTPUT_VALIDATION_FAILED, exception.getErrorCode());
    }

    @Test
    void rejectsConfidenceOutsideZeroToOne() {
        AiStructuredResult invalid = result("分析完成", 1.2d);

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> new AiStructuredResultValidator().validate(request(), invalid));

        assertEquals(AiErrorCodes.AI_OUTPUT_VALIDATION_FAILED, exception.getErrorCode());
    }

    private static AiOrchestrationRequest request() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY",
                "workflow-1", "REAL", new byte[]{1}, "image/jpeg", "分析图片", Map.of());
    }

    private static AiStructuredResult result(String summary, Double confidence) {
        return new AiStructuredResult(
                "request-1", "DIFY", "workflow-1", "1.0.0",
                AiCapabilityType.WORKFLOW, "SUCCEEDED", summary,
                List.of(), List.of(), List.of("人工复核"), confidence,
                List.of(), "dify:run-1", 10L);
    }
}
