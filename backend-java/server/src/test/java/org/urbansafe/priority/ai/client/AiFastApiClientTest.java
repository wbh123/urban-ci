package org.urbansafe.priority.ai.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.urbansafe.priority.ai.config.AiInferenceProperties;

/** FastAPI 客户端 HTTP 桩测试。 */
class AiFastApiClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AiFastApiClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AiInferenceProperties properties = new AiInferenceProperties();
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
        client = new AiFastApiClient(restClient, objectMapper, properties);
    }

    @Test
    void inferShouldReturnParsedResponseOnSuccess() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("req-1", "SUCCEEDED", "APPLICABLE",
                                "MOCK", "AI-DEFECT-MOCK-001", 0.1, 0.2))));

        AiInferenceResponse response = client.infer(new byte[]{1, 2}, metadata("req-1"), "req-1");

        assertEquals("req-1", response.requestId());
        assertEquals("SUCCEEDED", response.status());
        assertEquals("AI-DEFECT-MOCK-001", response.model().modelId());
        assertEquals(1, response.detections().size());
    }

    @Test
    void inferShouldRejectModelIdentityMismatch() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("req-1", "SUCCEEDED", "APPLICABLE",
                                "MOCK", "AI-OTHER", 0.1, 0.2))));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));

        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void requireModelReadyShouldAcceptApprovedCudaModel() {
        wireMock.stubFor(get(urlEqualTo("/internal/api/v1/ai/models/AI-REAL-001"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(modelBody("AI-REAL-001", "REAL", "APPROVED", true,
                                "CUDAExecutionProvider"))));

        AiRuntimeModelInfo model = client.requireModelReady("AI-REAL-001", "REAL");

        assertEquals("AI-REAL-001", model.modelId());
        assertEquals("CUDAExecutionProvider", model.executionProvider());
    }

    @Test
    void requireModelReadyShouldRejectCpuProvider() {
        wireMock.stubFor(get(urlEqualTo("/internal/api/v1/ai/models/AI-REAL-001"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(modelBody("AI-REAL-001", "REAL", "APPROVED", true,
                                "CPUExecutionProvider"))));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.requireModelReady("AI-REAL-001", "REAL"));

        assertEquals("AI_MODEL_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void inferShouldMapImageDecodeError() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody("req-1", "AI_IMAGE_DECODE_FAILED", "图片解码失败"))));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_IMAGE_DECODE_FAILED", ex.getErrorCode());
    }

    @Test
    void inferShouldMapServiceUnavailable() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody(errorBody("req-1", "AI_MODEL_UNAVAILABLE", "模型不可用"))));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_MODEL_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void inferShouldMapTimeout() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000)));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_SERVICE_TIMEOUT", ex.getErrorCode());
    }

    @Test
    void inferShouldRejectNonJsonResponse() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200).withBody("not-json")));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void inferShouldRejectRequestIdMismatch() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("other-request", "SUCCEEDED", "APPLICABLE",
                                "MOCK", "AI-DEFECT-MOCK-001", 0.1, 0.2))));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void inferShouldRejectInvalidBoundingBox() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(successBody("req-1", "SUCCEEDED", "APPLICABLE",
                                "MOCK", "AI-DEFECT-MOCK-001", 1.5, 0.2))));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    @Test
    void inferShouldRejectEmptyBody() {
        wireMock.stubFor(post(urlEqualTo("/internal/api/v1/ai/inferences"))
                .willReturn(aResponse().withStatus(200).withBody("")));

        AiFastApiException ex = assertThrows(AiFastApiException.class,
                () -> client.infer(new byte[]{1}, metadata("req-1"), "req-1"));
        assertEquals("AI_SERVICE_INVALID_RESPONSE", ex.getErrorCode());
    }

    private Map<String, Object> metadata(String requestId) {
        return client.buildMetadata(requestId, "MOCK", "asset-1", "image.jpg",
                "image/jpeg", "abc", "AI-DEFECT-MOCK-001");
    }

    private String successBody(String requestId, String status, String applicability,
            String mode, String modelId, double x, double y) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("status", status);
        body.put("mode", mode);
        body.put("model", Map.of("modelId", modelId, "modelName", "Model", "version", "1.0.0"));
        body.put("image", Map.of("width", 64, "height", 64,
                "qualityStatus", "ACCEPTABLE", "applicability", applicability));
        body.put("detections", List.of(Map.of(
                "sequence", 1, "classCode", "CRACK", "className", "裂缝", "confidence", 0.8,
                "boundingBox", Map.of("x", x, "y", y, "width", 0.2, "height", 0.2,
                        "coordinateType", "NORMALIZED_XYWH"))));
        body.put("summary", Map.of("detectionCount", 1, "classCounts", Map.of("CRACK", 1)));
        body.put("durationMs", 12);
        body.put("warnings", List.of());
        return toJson(body);
    }

    private String modelBody(String modelId, String mode, String status,
            boolean ready, String executionProvider) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelId", modelId);
        body.put("modelName", "Model");
        body.put("version", "1.0.0");
        body.put("mode", mode);
        body.put("status", status);
        body.put("supportedDefects", List.of("crack"));
        body.put("license", "MIT");
        body.put("weightSha256", "a".repeat(64));
        body.put("ready", ready);
        body.put("executionProvider", executionProvider);
        body.put("deviceId", 0);
        body.put("task", "CRACK_SEGMENTATION");
        body.put("adapter", "onnx-crack-segmentation-v1");
        return toJson(body);
    }

    private String errorBody(String requestId, String errorCode, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId);
        body.put("status", "REJECTED");
        body.put("errorCode", errorCode);
        body.put("errorMessage", message);
        body.put("mode", "MOCK");
        body.put("warnings", List.of());
        return toJson(body);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
}
