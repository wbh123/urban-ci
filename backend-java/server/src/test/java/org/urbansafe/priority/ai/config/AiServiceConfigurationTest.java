package org.urbansafe.priority.ai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.provider.AiInferenceProvider;

/**
 * 验证模型服务地址不可达时，Spring Boot 仍可完成客户端和提供者装配；
 * 网络连接只会在实际调用时发生。
 */
class AiServiceConfigurationTest {

    @Test
    void configurationShouldNotConnectToModelServiceDuringBeanCreation() {
        AiInferenceProperties properties = new AiInferenceProperties();
        properties.setBaseUrl("http://127.0.0.1:1");
        properties.setConnectTimeoutMs(10);
        properties.setReadTimeoutMs(10);
        AiServiceConfiguration configuration = new AiServiceConfiguration();

        RestClient restClient = configuration.aiFastApiRestClient(properties);
        AiFastApiClient client = configuration.aiFastApiClient(
                restClient, new ObjectMapper(), properties);
        AiInferenceProvider provider = configuration.aiInferenceProvider(client);

        assertNotNull(restClient);
        assertNotNull(client);
        assertEquals("FAST_API", provider.providerCode());
    }
}
