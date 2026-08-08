package org.urbansafe.priority.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

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
import org.urbansafe.priority.ai.client.AiImageQualityResponse;
import org.urbansafe.priority.ai.provider.AiProviderException;

@ExtendWith(MockitoExtension.class)
class AiImagePrecheckServiceTest {

    @Mock private AiFastApiClient client;

    private AiImagePrecheckService service;

    @BeforeEach
    void setUp() {
        service = new AiImagePrecheckService(client, new ObjectMapper());
    }

    @Test
    void shouldRejectLowQualityBeforeOnlineWorkflow() {
        AiOrchestrationRequest request = imageWorkflowRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(true, List.of("IMAGE_BLURRED")));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.precheck(request));

        assertEquals(AiErrorCodes.AI_IMAGE_LOW_QUALITY, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("IMAGE_BLURRED"));
    }

    @Test
    void shouldAttachPrecheckJsonForAcceptableImage() {
        AiOrchestrationRequest request = imageWorkflowRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenReturn(quality(false, List.of()));

        AiOrchestrationRequest result = service.precheck(request);

        String json = String.valueOf(result.inputs().get("precheckJson"));
        assertTrue(json.contains("LOCAL-IMAGE-QUALITY-001"));
        assertTrue(json.contains("\"lowQuality\":false"));
        assertEquals("asset-1", result.inputs().get("assetId"));
    }

    @Test
    void shouldSkipNonImageWorkflow() {
        AiOrchestrationRequest request = new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "DIFY-REPORT-DRAFT-001",
                "REAL", null, null, "生成报告草稿", Map.of("report", "x"));

        AiOrchestrationRequest result = service.precheck(request);

        assertEquals(request, result);
    }

    @Test
    void shouldMapFastApiTimeoutToRetryableProviderTimeout() {
        AiOrchestrationRequest request = imageWorkflowRequest();
        when(client.analyzeImageQuality(request.imageBytes(), request.requestId()))
                .thenThrow(new AiFastApiException("AI_SERVICE_TIMEOUT", "timeout"));

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> service.precheck(request));

        assertEquals(AiErrorCodes.AI_PROVIDER_TIMEOUT, ex.getErrorCode());
    }

    private static AiOrchestrationRequest imageWorkflowRequest() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "DIFY-IMAGE-ANALYSIS-001",
                "REAL", new byte[]{1, 2, 3}, "image/jpeg", "分析图片",
                Map.of("assetId", "asset-1", "requestCode", "AI-1"));
    }

    private static AiImageQualityResponse quality(boolean lowQuality, List<String> reasons) {
        return new AiImageQualityResponse(
                "request-1", "LOCAL-IMAGE-QUALITY-001", "0.1.0", "SUCCEEDED", "DECODED",
                "image/jpeg", 640, 480, 0.5, 0.1, 0.03,
                false, false, false, lowQuality, false, lowQuality, lowQuality, reasons);
    }
}
