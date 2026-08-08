package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            return result(request, "SPRING_AI");
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

    @Test
    void appliesImagePrecheckBeforeProviderExecution() {
        AiImagePrecheckService precheck = mock(AiImagePrecheckService.class);
        AiOrchestrationRequest original = imageWorkflowRequest();
        AiOrchestrationRequest enriched = new AiOrchestrationRequest(
                original.requestId(), original.capabilityType(), original.requestedProviderCode(),
                original.modelCode(), original.taskMode(), original.imageBytes(), original.contentType(),
                original.prompt(), Map.of("precheckJson", "{\"lowQuality\":false}"));
        when(precheck.precheck(original)).thenReturn(enriched);
        AtomicBoolean providerSawPrecheck = new AtomicBoolean(false);
        AiCapabilityProvider primary = provider("DIFY", request -> {
            providerSawPrecheck.set(request.inputs().containsKey("precheckJson"));
            return result(request, "DIFY");
        });
        AiOrchestrationProperties properties = new AiOrchestrationProperties();
        properties.setDefaultVisionProvider("DIFY");
        AiOrchestrationService service = new AiOrchestrationService(
                new AiProviderRouter(List.of(primary), properties),
                new AiStructuredResultValidator(),
                precheck);

        service.execute(original);

        verify(precheck).precheck(original);
        assertEquals(true, providerSawPrecheck.get());
    }

    @Test
    void lowQualityPrecheckStopsBeforeProviderExecution() {
        AiImagePrecheckService precheck = mock(AiImagePrecheckService.class);
        AiOrchestrationRequest original = imageWorkflowRequest();
        when(precheck.precheck(original)).thenThrow(
                new AiProviderException(AiErrorCodes.AI_IMAGE_LOW_QUALITY, "图片质量不足"));
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        AiCapabilityProvider primary = provider("DIFY", request -> {
            providerCalled.set(true);
            return result(request, "DIFY");
        });
        AiOrchestrationProperties properties = new AiOrchestrationProperties();
        properties.setDefaultVisionProvider("DIFY");
        AiOrchestrationService service = new AiOrchestrationService(
                new AiProviderRouter(List.of(primary), properties),
                new AiStructuredResultValidator(),
                precheck);

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.execute(original));

        assertEquals(AiErrorCodes.AI_IMAGE_LOW_QUALITY, ex.getErrorCode());
        assertFalse(providerCalled.get());
    }

    private static AiOrchestrationRequest request() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.VISION_INFERENCE, "DIFY",
                "model-1", "REAL", new byte[]{1}, "image/jpeg", "分析图片", Map.of());
    }

    private static AiOrchestrationRequest imageWorkflowRequest() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.VISION_INFERENCE, "DIFY",
                "DIFY-IMAGE-ANALYSIS-001", "REAL", new byte[]{1}, "image/jpeg",
                "分析图片", Map.of("assetId", "asset-1"));
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

    private static AiStructuredResult result(AiOrchestrationRequest request, String providerCode) {
        return new AiStructuredResult(
                request.requestId(), providerCode, request.modelCode(), "1.0.0",
                request.capabilityType(), "SUCCEEDED", "分析完成",
                List.of(), List.of(), List.of("人工复核"), 0.7d,
                List.of(), providerCode.toLowerCase() + ":ref-1", 10L);
    }

    @FunctionalInterface
    private interface Executor {
        AiStructuredResult execute(AiOrchestrationRequest request);
    }
}
