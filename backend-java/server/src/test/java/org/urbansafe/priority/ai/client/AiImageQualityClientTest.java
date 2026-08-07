package org.urbansafe.priority.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.urbansafe.priority.ai.config.AiInferenceProperties;

class AiImageQualityClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AiFastApiClient client;

    @BeforeEach
    void setUp() {
        AiInferenceProperties properties = new AiInferenceProperties();
        properties.setBaseUrl(wireMock.baseUrl());
        properties.setConnectTimeoutMs(1000);
        properties.setReadTimeoutMs(200);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        client = new AiFastApiClient(
                RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build(),
                new ObjectMapper(), properties);
    }

    @Test
    void analyzeImageQualityShouldParseStableResponse() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-quality"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"requestId":"quality-1","modelId":"LOCAL-IMAGE-QUALITY-001",
                                "modelVersion":"0.1.0","status":"SUCCEEDED","decodeStatus":"DECODED",
                                "contentType":"image/jpeg","width":640,"height":480,
                                "brightness":0.51,"contrast":0.12,"sharpness":0.03,
                                "blank":false,"underexposed":false,"overexposed":false,
                                "blurDetected":false,"lowResolution":false,"lowQuality":false,
                                "reshootRecommended":false,"reasons":[]}
                                """)));

        AiImageQualityResponse result = client.analyzeImageQuality(new byte[]{1, 2, 3}, "quality-1");

        assertEquals("quality-1", result.requestId());
        assertEquals(false, result.lowQuality());
        assertEquals(0.03d, result.sharpness());
    }

    @Test
    void analyzeImageQualityShouldRejectMissingRequiredFields() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-quality"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"requestId\":\"quality-1\",\"lowQuality\":true}")));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.analyzeImageQuality(new byte[]{1}, "quality-1"));

        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void analyzeImageQualityShouldMapTimeout() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/image-quality"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000)));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.analyzeImageQuality(new byte[]{1}, "quality-1"));

        assertEquals("AI_SERVICE_TIMEOUT", ex.getErrorCode());
    }
}
