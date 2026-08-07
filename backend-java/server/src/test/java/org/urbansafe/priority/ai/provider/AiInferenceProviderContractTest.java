package org.urbansafe.priority.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiFastApiClient;

/**
 * 模型提供者契约测试：业务只依赖统一接口，底层 FastAPI/CUDA 错误转换为通用提供者错误。
 */
class AiInferenceProviderContractTest {

    @Test
    void providerExceptionShouldKeepStableErrorCodeAndMessage() {
        AiProviderException exception = new AiProviderException(
                "AI_SERVICE_UNAVAILABLE", "模型服务不可用");

        assertEquals("AI_SERVICE_UNAVAILABLE", exception.getErrorCode());
        assertEquals("模型服务不可用", exception.getMessage());
    }

    @Test
    void fastApiProviderShouldTranslateInfrastructureException() {
        AiFastApiClient client = org.mockito.Mockito.mock(AiFastApiClient.class);
        org.mockito.Mockito.when(client.infer(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new AiFastApiException("AI_SERVICE_TIMEOUT", "FastAPI 调用超时"));
        FastApiAiInferenceProvider provider = new FastApiAiInferenceProvider(client);

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                provider.infer(new byte[]{1}, Map.of("mode", "MOCK"), "request-1"));

        assertEquals("AI_SERVICE_TIMEOUT", exception.getErrorCode());
        assertEquals("模型服务调用超时", exception.getMessage());
    }
}
