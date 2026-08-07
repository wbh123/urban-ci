package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.provider.AiProviderException;

class AiProviderRouterTest {

    @Test
    void routesExplicitProvider() {
        AiProviderRouter router = new AiProviderRouter(
                List.of(provider("FAST_API", true, true), provider("DIFY", true, true)),
                defaults("FAST_API"));

        assertEquals("DIFY", router.route(request("DIFY")).providerCode());
    }

    @Test
    void usesConfiguredDefaultProvider() {
        AiProviderRouter router = new AiProviderRouter(
                List.of(provider("FAST_API", true, true)), defaults("FAST_API"));

        assertEquals("FAST_API", router.route(request(null)).providerCode());
    }

    @Test
    void rejectsDisabledProvider() {
        AiProviderRouter router = new AiProviderRouter(
                List.of(provider("DIFY", false, true)), defaults("DIFY"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> router.route(request("DIFY")));

        assertEquals(AiErrorCodes.AI_PROVIDER_DISABLED, exception.getErrorCode());
    }

    @Test
    void rejectsUnconfiguredProvider() {
        AiProviderRouter router = new AiProviderRouter(
                List.of(provider("DIFY", true, false)), defaults("DIFY"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> router.route(request("DIFY")));

        assertEquals(AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED, exception.getErrorCode());
    }

    private static AiOrchestrationRequest request(String providerCode) {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.VISION_INFERENCE, providerCode,
                "model-1", "REAL", new byte[]{1}, "image/jpeg", "分析图片", Map.of());
    }

    private static AiOrchestrationProperties defaults(String code) {
        AiOrchestrationProperties properties = new AiOrchestrationProperties();
        properties.setDefaultVisionProvider(code);
        properties.setDefaultWorkflowProvider(code);
        properties.setDefaultTextProvider(code);
        return properties;
    }

    private static AiCapabilityProvider provider(String code, boolean enabled, boolean configured) {
        return new StubProvider(code, enabled, configured);
    }

    private record StubProvider(
            String providerCode,
            boolean enabled,
            boolean configured) implements AiCapabilityProvider {

        @Override
        public Set<AiCapabilityType> capabilities() {
            return Set.of(AiCapabilityType.VISION_INFERENCE);
        }

        @Override
        public AiStructuredResult execute(AiOrchestrationRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
