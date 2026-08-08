package org.urbansafe.priority.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AiImageApplicabilityClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AiImageApplicabilityClient client;

    @BeforeEach
    void setUp() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        client = new AiImageApplicabilityClient(
                RestClient.builder().baseUrl(wireMock.baseUrl()).requestFactory(factory).build(),
                new ObjectMapper());
    }

    @Test
    void analyzeShouldParseStableApplicableResponse() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-applicability"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"requestId":"app-1","modelId":"LOCAL-IMAGE-APPLICABILITY-001",
                                "modelVersion":"1.0.0","status":"SUCCEEDED",
                                "decision":"APPLICABLE","confidence":0.97,
                                "scores":{"APPLICABLE":0.97,"NOT_APPLICABLE":0.03},
                                "allowDify":true,"reason":"HIGH_CONFIDENCE_APPLICABLE"}
                                """)));

        AiImageApplicabilityResponse result = client.analyze(new byte[]{1, 2, 3}, "app-1");

        assertEquals("app-1", result.requestId());
        assertEquals("APPLICABLE", result.decision());
        assertTrue(result.allowDify());
        assertEquals(0.97d, result.confidence());
    }

    @Test
    void analyzeShouldParseStableNotApplicableResponse() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-applicability"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"requestId":"app-2","modelId":"LOCAL-IMAGE-APPLICABILITY-001",
                                "modelVersion":"1.0.0","status":"SUCCEEDED",
                                "decision":"NOT_APPLICABLE","confidence":0.98,
                                "scores":{"APPLICABLE":0.02,"NOT_APPLICABLE":0.98},
                                "allowDify":false,"reason":"HIGH_CONFIDENCE_NOT_APPLICABLE"}
                                """)));

        AiImageApplicabilityResponse result = client.analyze(new byte[]{1, 2, 3}, "app-2");

        assertEquals("NOT_APPLICABLE", result.decision());
        assertFalse(result.allowDify());
    }

    @Test
    void analyzeShouldRejectMissingRequiredFields() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-applicability"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"requestId\":\"app-1\",\"decision\":\"UNCERTAIN\"}")));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.analyze(new byte[]{1}, "app-1"));

        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void analyzeShouldRejectContradictoryNotApplicableResponse() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-applicability"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"requestId":"app-1","modelId":"LOCAL-IMAGE-APPLICABILITY-001",
                                "modelVersion":"1.0.0","status":"SUCCEEDED",
                                "decision":"NOT_APPLICABLE","confidence":0.98,
                                "scores":{"APPLICABLE":0.02,"NOT_APPLICABLE":0.98},
                                "allowDify":true,"reason":"BROKEN"}
                                """)));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.analyze(new byte[]{1}, "app-1"));

        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void analyzeShouldMapStableImageError() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-applicability"))
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"requestId":"app-1","status":"REJECTED",
                                "errorCode":"AI_IMAGE_DECODE_FAILED","errorMessage":"图片解码失败"}
                                """)));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.analyze(new byte[]{1}, "app-1"));

        assertEquals("AI_IMAGE_DECODE_FAILED", ex.getErrorCode());
    }

    @Test
    void analyzeShouldMapTimeout() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-applicability"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(5000)));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.analyze(new byte[]{1}, "app-1"));

        assertEquals("AI_SERVICE_TIMEOUT", ex.getErrorCode());
    }
}
