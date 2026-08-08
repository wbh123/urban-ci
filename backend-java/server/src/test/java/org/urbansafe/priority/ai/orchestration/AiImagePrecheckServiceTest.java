package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiImageApplicabilityClient;
import org.urbansafe.priority.ai.client.AiImageApplicabilityResponse;
import org.urbansafe.priority.ai.client.AiImageQualityResponse;
import org.urbansafe.priority.ai.provider.AiProviderException;

@ExtendWith(MockitoExtension.class)
class AiImagePrecheckServiceTest {

    @Mock private AiFastApiClient client;
    @Mock private AiImageApplicabilityClient applicabilityClient;

    private ObjectMapper objectMapper;
    private AiImagePrecheckService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AiImagePrecheckService(client, applicabilityClient, objectMapper);
    }

    @Test
    void shouldRejectLowQualityBeforeSemanticAndOnlineWorkflowForRegisteredModelCode() {
        AiOrchestrationRequest request = imageModelRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(true, List.of("IMAGE_BLURRED")));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.precheck(request));

        assertEquals(AiErrorCodes.AI_IMAGE_LOW_QUALITY, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("IMAGE_BLURRED"));
        verifyNoInteractions(applicabilityClient);
    }

    @Test
    void shouldAttachBackwardCompatibleCombinedPrecheckForApplicableImage() throws Exception {
        AiOrchestrationRequest request = imageModelRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(false, List.of()));
        when(applicabilityClient.analyze(request.imageBytes(), request.requestId()))
                .thenReturn(applicability("APPLICABLE", true, 0.97, "HIGH_CONFIDENCE_APPLICABLE"));

        AiOrchestrationRequest result = service.precheck(request);

        assertFalse(result.inputs().containsKey("applicabilityPrecheckJson"));
        JsonNode precheck = objectMapper.readTree(String.valueOf(result.inputs().get("precheckJson")));
        assertEquals("LOCAL-IMAGE-QUALITY-001", precheck.path("modelId").asText());
        assertFalse(precheck.path("lowQuality").asBoolean());
        assertEquals(
                "LOCAL-IMAGE-APPLICABILITY-001",
                precheck.path("applicabilityPrecheck").path("modelId").asText());
        assertEquals(
                "APPLICABLE",
                precheck.path("applicabilityPrecheck").path("decision").asText());
        assertTrue(precheck.path("applicabilityPrecheck").path("allowDify").asBoolean());
        assertEquals("asset-1", result.inputs().get("assetId"));
    }

    @Test
    void shouldAllowUncertainSemanticDecisionToContinueDify() throws Exception {
        AiOrchestrationRequest request = imageModelRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(false, List.of()));
        when(applicabilityClient.analyze(request.imageBytes(), request.requestId()))
                .thenReturn(applicability("UNCERTAIN", true, 0.51, "LOW_CONFIDENCE"));

        AiOrchestrationRequest result = service.precheck(request);

        JsonNode precheck = objectMapper.readTree(String.valueOf(result.inputs().get("precheckJson")));
        JsonNode semantic = precheck.path("applicabilityPrecheck");
        assertEquals("UNCERTAIN", semantic.path("decision").asText());
        assertTrue(semantic.path("allowDify").asBoolean());
    }

    @Test
    void shouldRejectHighConfidenceNotApplicableBeforeDify() {
        AiOrchestrationRequest request = imageModelRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(false, List.of()));
        when(applicabilityClient.analyze(request.imageBytes(), request.requestId()))
                .thenReturn(applicability(
                        "NOT_APPLICABLE", false, 0.98, "HIGH_CONFIDENCE_NOT_APPLICABLE"));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.precheck(request));

        assertEquals(AiErrorCodes.AI_IMAGE_NOT_APPLICABLE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("HIGH_CONFIDENCE_NOT_APPLICABLE"));
    }

    @Test
    void shouldAlsoAcceptWorkflowCodeAlias() {
        AiOrchestrationRequest request = new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "DIFY-IMAGE-ANALYSIS-001",
                "REAL", new byte[]{1, 2, 3}, "image/jpeg", "分析图片",
                Map.of("assetId", "asset-1", "requestCode", "AI-1"));
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(false, List.of()));
        when(applicabilityClient.analyze(request.imageBytes(), request.requestId()))
                .thenReturn(applicability("APPLICABLE", true, 0.91, "HIGH_CONFIDENCE_APPLICABLE"));

        AiOrchestrationRequest result = service.precheck(request);

        assertTrue(result.inputs().containsKey("precheckJson"));
        assertFalse(result.inputs().containsKey("applicabilityPrecheckJson"));
    }

    @Test
    void shouldSkipNonImageWorkflow() {
        AiOrchestrationRequest request = new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "DIFY-REPORT-DRAFT-001",
                "REAL", null, null, "生成报告草稿", Map.of("report", "x"));

        AiOrchestrationRequest result = service.precheck(request);

        assertEquals(request, result);
        verifyNoInteractions(client, applicabilityClient);
    }

    @Test
    void shouldMapFastApiTimeoutToRetryableProviderTimeout() {
        AiOrchestrationRequest request = imageModelRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenThrow(new AiFastApiException("AI_SERVICE_TIMEOUT", "timeout"));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.precheck(request));

        assertEquals(AiErrorCodes.AI_PROVIDER_TIMEOUT, ex.getErrorCode());
        verifyNoInteractions(applicabilityClient);
    }

    @Test
    void shouldMapApplicabilityTimeoutToRetryableProviderTimeout() {
        AiOrchestrationRequest request = imageModelRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(false, List.of()));
        when(applicabilityClient.analyze(request.imageBytes(), request.requestId()))
                .thenThrow(new AiFastApiException("AI_SERVICE_TIMEOUT", "timeout"));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.precheck(request));

        assertEquals(AiErrorCodes.AI_PROVIDER_TIMEOUT, ex.getErrorCode());
    }

    private static AiOrchestrationRequest imageModelRequest() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "AI-DIFY-WORKFLOW-001",
                "REAL", new byte[]{1, 2, 3}, "image/jpeg", "分析图片",
                Map.of("assetId", "asset-1", "requestCode", "AI-1"));
    }

    private static AiImageQualityResponse quality(boolean lowQuality, List<String> reasons) {
        return new AiImageQualityResponse(
                "request-1", "LOCAL-IMAGE-QUALITY-001", "0.1.0", "SUCCEEDED", "DECODED",
                "image/jpeg", 640, 480, 0.5, 0.1, 0.03,
                false, false, false, lowQuality, false, lowQuality, lowQuality, reasons);
    }

    private static AiImageApplicabilityResponse applicability(
            String decision, boolean allowDify, double confidence, String reason) {
        return new AiImageApplicabilityResponse(
                "request-1",
                "LOCAL-IMAGE-APPLICABILITY-001",
                "1.0.0",
                "SUCCEEDED",
                decision,
                confidence,
                Map.of("APPLICABLE", allowDify ? confidence : 1.0 - confidence,
                        "NOT_APPLICABLE", allowDify ? 1.0 - confidence : confidence),
                allowDify,
                reason);
    }
}
