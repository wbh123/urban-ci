package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.provider.AiProviderException;

class AiOrchestrationServiceTest {

    @Test
    void doesNotSilentlyFallbackAfterSelectedProviderFails() {
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        AiCapabilityProvider primary = provider("DIFY", request -> {
            throw new AiProviderException(AiErrorCodes.AI_PROVIDER_TIMEOUT, "人工智能提供者调用超时");
        });
        AiCapabilityProvider fallback = provider("SPRING_AI", request -> {
            fallbackCalled.set(true);
            return result("SPRING_AI");
        });
        AiOrchestrationProperties properties = new AiOrchestrationProperties();
        properties.setDefaultVisionProvider("DIFY");
        AiOrchestrationService service = new AiOrchestrationService(
                new AiProviderRouter(List.of(primary, fallback), properties),
                new AiStructuredResultValidator());

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> service.execute(request()));

        assertEquals(AiErrorCodes.AI_PROVIDER_TIMEOUT, exception.getErrorCode());
        assertFalse(fallbackCalled.get());
    }

    private static AiOrchestrationRequest request() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.VISION_INFERENCE, "DIFY",
                "model-1", "REAL", new byte[]{1}, "image/jpeg", "分析图片", Map.of());
    }

    private static AiCapabilityProvider provider(String code, Executor executor) {
        return new AiCapabilityProvider() {
            public String providerCode() { return code; }
            public boolean enabled() { return true; }
            public boolean configured() { return true; }
            public Set<AiCapabilityType> capabilities() { return Set.of(AiCapabilityType.VISION_INFERENCE); }
            public AiStructuredResult execute(AiOrchestrationRequest request) { return executor.execute(request); }
        };
    }

    private static AiStructuredResult result(String providerCode) {
        return new AiStructuredResult(
                "request-1", providerCode, "model-1", "1.0.0",
                AiCapabilityType.VISION_INFERENCE, "SUCCEEDED", "分析完成",
                List.of(), List.of(), List.of("人工复核"), 0.7d,
                List.of(), providerCode.toLowerCase() + ":ref-1", 10L);
    }

    @FunctionalInterface
    private interface Executor {
        AiStructuredResult execute(AiOrchestrationRequest request);
    }
}
