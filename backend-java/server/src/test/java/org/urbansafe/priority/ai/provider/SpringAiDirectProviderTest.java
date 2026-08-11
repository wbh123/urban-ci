package org.urbansafe.priority.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.config.SpringAiProviderProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;

class SpringAiDirectProviderTest {

    @Test
    void supportsTextGenerationOnly() {
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> "{\"summary\":\"ok\"}", new ObjectMapper(), configuredProperties());

        assertEquals(Set.of(AiCapabilityType.TEXT_GENERATION), provider.capabilities());
    }

    @Test
    void convertsSuccessfulStructuredJson() {
        SpringAiProviderProperties properties = configuredProperties();
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> "{\"summary\":\"需要补拍近景\",\"confidence\":0.66,"
                        + "\"recommendations\":[\"补拍带标尺照片\"],\"warnings\":[\"辅助结果\"]}",
                new ObjectMapper(), properties);

        AiStructuredResult result = provider.execute(request());

        assertEquals("SPRING_AI", result.providerCode());
        assertEquals("deepseek-v4-flash", result.modelCode());
        assertEquals("需要补拍近景", result.summary());
        assertEquals(0.66d, result.confidence());
    }

    @Test
    void isNotConfiguredWithoutApiKey() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();
        properties.setEnabled(true);
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> "{}", new ObjectMapper(), properties);

        assertFalse(provider.configured());
    }

    @Test
    void rejectsInvalidJson() {
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> "not-json", new ObjectMapper(), configuredProperties());

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_INVALID_RESPONSE, exception.getErrorCode());
    }

    @Test
    void preservesTimeoutErrorCodeFromGateway() {
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> {
                    throw new AiProviderException(
                            AiErrorCodes.AI_PROVIDER_TIMEOUT, "在线模型调用超时");
                }, new ObjectMapper(), configuredProperties());

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_PROVIDER_TIMEOUT, exception.getErrorCode());
    }

    @Test
    void preservesAuthenticationErrorCodeFromGateway() {
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> {
                    throw new AiProviderException(
                            AiErrorCodes.AI_PROVIDER_AUTH_FAILED, "在线模型身份认证失败");
                }, new ObjectMapper(), configuredProperties());

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_PROVIDER_AUTH_FAILED, exception.getErrorCode());
    }

    @Test
    void rejectsMissingSummary() {
        SpringAiDirectProvider provider = new SpringAiDirectProvider(
                request -> "{\"confidence\":0.6}", new ObjectMapper(), configuredProperties());

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_INVALID_RESPONSE, exception.getErrorCode());
    }

    private static AiOrchestrationRequest request() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.TEXT_GENERATION, "SPRING_AI",
                null, "REAL", null, null, "解释风险并给出辅助建议", Map.of());
    }

    private static SpringAiProviderProperties configuredProperties() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        return properties;
    }
}
