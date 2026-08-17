package org.urbansafe.priority.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.workflow.AiWorkflowDefinition;
import org.urbansafe.priority.ai.workflow.AiWorkflowRegistry;

/** Dify 客户端 HTTP 桩测试。 */
class DifyApiClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private DifyApiClient client;
    private AiWorkflowRegistry workflowRegistry;

    @BeforeEach
    void setUp() {
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(1000);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
        workflowRegistry = mock(AiWorkflowRegistry.class);
        client = new DifyApiClient(restClient, new ObjectMapper(), properties, workflowRegistry);
    }

    @Test
    void uploadShouldUseImageWorkflowKeyAndSendCorrectMimeType() {
        when(workflowRegistry.requireByWorkflowCode("AI-DIFY-WORKFLOW-001"))
                .thenReturn(definition("DIFY-IMAGE-ANALYSIS-001", "image-key", true));
        wireMock.stubFor(post(urlEqualTo("/files/upload"))
                .withHeader("Authorization", equalTo("Bearer image-key"))
                .withMultipartRequestBody(aMultipart()
                        .withName("file")
                        .withHeader("Content-Disposition", containing("filename=\"inspection-image.jpg\""))
                        .withHeader("Content-Type", containing("image/jpeg")))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"file-1\"}")));
        wireMock.stubFor(post(urlEqualTo("/workflows/run"))
                .withHeader("Authorization", equalTo("Bearer image-key"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"workflow_run_id\":\"run-1\",\"data\":{\"status\":\"succeeded\",\"outputs\":{\"result\":{\"summary\":\"ok\"}}}}")));

        JsonNode response = client.run(request("AI-DIFY-WORKFLOW-001", new byte[]{1, 2, 3}));

        assertEquals("succeeded", response.path("data").path("status").asText());
    }

    @Test
    void textWorkflowShouldUseItsOwnApiKeyAndSendOnlyDeclaredInputs() {
        when(workflowRegistry.requireByWorkflowCode("DIFY-KNOWLEDGE-QA-001"))
                .thenReturn(definition("DIFY-KNOWLEDGE-QA-001", "qa-key", true));
        wireMock.stubFor(post(urlEqualTo("/workflows/run"))
                .withHeader("Authorization", equalTo("Bearer qa-key"))
                .withRequestBody(equalToJson("""
                        {
                          "inputs": {
                            "assetId": "asset-1",
                            "requestCode": "request-1"
                          },
                          "response_mode": "blocking",
                          "user": "request-1"
                        }
                        """))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"workflow_run_id\":\"run-qa\",\"data\":{\"status\":\"succeeded\",\"outputs\":{\"result\":{\"summary\":\"answer\"}}}}")));

        JsonNode response = client.run(request("DIFY-KNOWLEDGE-QA-001", null));

        assertEquals("succeeded", response.path("data").path("status").asText());
    }

    private static AiOrchestrationRequest request(String modelCode, byte[] image) {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", modelCode, "REAL",
                image, image == null ? null : "image/jpeg", "执行工作流",
                Map.of("assetId", "asset-1", "requestCode", "request-1"));
    }

    private static AiWorkflowDefinition definition(String code, String key, boolean configured) {
        return new AiWorkflowDefinition(
                code, code, "测试工作流", "DIFY", "WORKFLOW", "test",
                "v1", "1.0", "1.0", true, "VALIDATING", false,
                300000, 3, Map.of(), key, "app", configured);
    }
}
