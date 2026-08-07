package org.urbansafe.priority.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.DefaultSpringAiChatGateway;
import org.urbansafe.priority.ai.client.DifyApiClient;
import org.urbansafe.priority.ai.client.DifyWorkflowClient;
import org.urbansafe.priority.ai.client.SpringAiChatGateway;
import org.urbansafe.priority.ai.execution.AiExecutionProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiImagePrecheckService;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationProperties;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationService;
import org.urbansafe.priority.ai.orchestration.AiProviderRouter;
import org.urbansafe.priority.ai.orchestration.AiStructuredResultValidator;
import org.urbansafe.priority.ai.provider.AiInferenceProvider;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.provider.DifyWorkflowProvider;
import org.urbansafe.priority.ai.provider.FastApiAiInferenceProvider;
import org.urbansafe.priority.ai.provider.SpringAiDirectProvider;
import org.urbansafe.priority.ai.workflow.AiWorkflowRegistry;

/** 人工智能服务与第七阶段混合提供者配置。 */
@Configuration
@EnableConfigurationProperties({
        AiInferenceProperties.class,
        AiOrchestrationProperties.class,
        DifyProperties.class,
        AiExecutionProperties.class,
        SpringAiProviderProperties.class
})
public class AiServiceConfiguration {

    /** 构建 FastAPI HTTP 客户端；创建客户端不会连接模型服务。 */
    @Bean
    public RestClient aiFastApiRestClient(AiInferenceProperties properties) {
        SimpleClientHttpRequestFactory factory = requestFactory(
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public AiFastApiClient aiFastApiClient(
            @Qualifier("aiFastApiRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            AiInferenceProperties properties) {
        return new AiFastApiClient(restClient, objectMapper, properties);
    }

    @Bean
    public FastApiAiInferenceProvider aiInferenceProvider(AiFastApiClient client) {
        return new FastApiAiInferenceProvider(client);
    }

    @Bean
    public RestClient difyRestClient(DifyProperties properties) {
        SimpleClientHttpRequestFactory factory = requestFactory(
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public DifyWorkflowClient difyWorkflowClient(
            @Qualifier("difyRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            DifyProperties properties,
            AiWorkflowRegistry workflowRegistry) {
        return new DifyApiClient(restClient, objectMapper, properties, workflowRegistry);
    }

    @Bean
    public DifyWorkflowProvider difyWorkflowProvider(
            DifyWorkflowClient client,
            ObjectMapper objectMapper,
            DifyProperties properties,
            AiWorkflowRegistry workflowRegistry) {
        return new DifyWorkflowProvider(client, objectMapper, properties, workflowRegistry);
    }

    @Bean
    @ConditionalOnBean(ChatClient.Builder.class)
    public SpringAiChatGateway springAiChatGateway(ChatClient.Builder builder) {
        return new DefaultSpringAiChatGateway(builder);
    }

    @Bean
    public SpringAiDirectProvider springAiDirectProvider(
            ObjectProvider<SpringAiChatGateway> gatewayProvider,
            ObjectMapper objectMapper,
            SpringAiProviderProperties properties) {
        SpringAiChatGateway gateway = gatewayProvider.getIfAvailable(() -> request -> {
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED,
                    "Spring AI 在线模型尚未完成配置");
        });
        return new SpringAiDirectProvider(gateway, objectMapper, properties);
    }

    @Bean
    public AiStructuredResultValidator aiStructuredResultValidator() {
        return new AiStructuredResultValidator();
    }

    @Bean
    public AiProviderRouter aiProviderRouter(
            List<AiCapabilityProvider> providers,
            AiOrchestrationProperties properties) {
        return new AiProviderRouter(providers, properties);
    }

    @Bean
    public AiOrchestrationService aiOrchestrationService(
            AiProviderRouter router,
            AiStructuredResultValidator validator,
            AiImagePrecheckService imagePrecheckService) {
        return new AiOrchestrationService(router, validator, imagePrecheckService);
    }

    private static SimpleClientHttpRequestFactory requestFactory(
            int connectTimeoutMs,
            int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return factory;
    }
}
