package org.urbansafe.priority.ai.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.client.AiFastApiException;
import org.urbansafe.priority.ai.client.AiRuntimeModelInfo;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.repository.AiModelCatalogRepository;
import org.urbansafe.priority.common.exception.ResourceConflictException;

@ExtendWith(MockitoExtension.class)
class AiModelCatalogServiceTest {

    @Mock private AiModelCatalogRepository repository;
    @Mock private AiFastApiClient fastApiClient;
    @Mock private AiCapabilityProvider difyProvider;
    private AiModelCatalogService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AiModelCatalogService(repository, fastApiClient, List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCombinesBusinessGovernanceAndRuntimeReadiness() {
        when(repository.listVisibleModels()).thenReturn(List.of(
                Map.of(
                        "modelId", "AI-CRACK-001",
                        "modelName", "裂缝模型",
                        "modelVersion", "1.0.0",
                        "modelType", "OBJECT_DETECTION",
                        "mode", "REAL",
                        "status", "APPROVED",
                        "deploymentStage", "DEMO",
                        "formalEvidenceEnabled", false,
                        "providerCode", "FAST_API",
                        "capabilityType", "VISION_INFERENCE"),
                Map.of(
                        "modelId", "AI-DEFECT-MOCK-001",
                        "modelName", "模拟模型",
                        "modelVersion", "0.1.0",
                        "modelType", "OBJECT_DETECTION",
                        "mode", "MOCK",
                        "status", "MOCK",
                        "deploymentStage", "DEMO",
                        "formalEvidenceEnabled", false,
                        "providerCode", "FAST_API",
                        "capabilityType", "VISION_INFERENCE")));
        when(fastApiClient.requireModelReady("AI-CRACK-001", "REAL")).thenReturn(
                new AiRuntimeModelInfo(
                        "AI-CRACK-001", "裂缝模型", "1.0.0", "REAL", "APPROVED",
                        List.of("crack"), "MIT", "abc", true,
                        "CUDAExecutionProvider", 0, "CRACK_SEGMENTATION",
                        "onnx-crack-segmentation-v1"));
        when(fastApiClient.requireModelReady("AI-DEFECT-MOCK-001", "MOCK")).thenReturn(
                new AiRuntimeModelInfo(
                        "AI-DEFECT-MOCK-001", "模拟模型", "0.1.0", "MOCK", "MOCK",
                        List.of("crack"), "PROJECT-INTERNAL-MOCK", null, true,
                        "DETERMINISTIC_MOCK", null, "DEFECT_DETECTION",
                        "deterministic-mock-v1"));

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) service.list().get("content");

        assertEquals(2, content.size());
        assertTrue((Boolean) content.get(0).get("runtimeReady"));
        assertEquals("CUDAExecutionProvider", content.get(0).get("executionProvider"));
        assertFalse((Boolean) content.get(0).get("formalEvidenceEnabled"));
        assertTrue((Boolean) content.get(1).get("selectable"));
        assertEquals("DETERMINISTIC_MOCK", content.get(1).get("executionProvider"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void unavailableRealModelIsVisibleButNotSelectable() {
        when(repository.listVisibleModels()).thenReturn(List.of(Map.of(
                "modelId", "AI-BUILDING-YOLOX-001",
                "modelType", "OBJECT_DETECTION",
                "mode", "REAL",
                "status", "APPROVED",
                "deploymentStage", "VALIDATING",
                "formalEvidenceEnabled", false,
                "providerCode", "FAST_API",
                "capabilityType", "VISION_INFERENCE")));
        when(fastApiClient.requireModelReady("AI-BUILDING-YOLOX-001", "REAL"))
                .thenThrow(new AiFastApiException("AI_MODEL_UNAVAILABLE", "模型未加载"));

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) service.list().get("content");

        assertFalse((Boolean) content.get(0).get("runtimeReady"));
        assertFalse((Boolean) content.get(0).get("selectable"));
        assertEquals("AI_MODEL_UNAVAILABLE", content.get(0).get("runtimeErrorCode"));
    }

    @Test
    void selectableReadyFastApiVisionModelCanBecomeDefault() {
        when(repository.listVisibleModels()).thenReturn(List.of(Map.of(
                "modelId", "AI-VISION-LOCAL-001",
                "modelName", "本地视觉模型",
                "modelVersion", "1.1.0",
                "modelType", "OBJECT_DETECTION",
                "mode", "REAL",
                "status", "APPROVED",
                "deploymentStage", "DEMO",
                "formalEvidenceEnabled", false,
                "providerCode", "FAST_API",
                "capabilityType", "VISION_INFERENCE")));
        when(fastApiClient.requireModelReady("AI-VISION-LOCAL-001", "REAL")).thenReturn(
                new AiRuntimeModelInfo(
                        "AI-VISION-LOCAL-001", "本地视觉模型", "1.1.0", "REAL", "APPROVED",
                        List.of("crack"), "Apache-2.0", "abc", true,
                        "PyTorch-CUDA", 0, "ZERO_SHOT_VISUAL_DEFECT",
                        "grounded-sam2-v1"));

        Map<String, Object> model = service.requireSelectableVisionModel("AI-VISION-LOCAL-001");

        assertEquals("AI-VISION-LOCAL-001", model.get("modelId"));
        assertTrue((Boolean) model.get("selectable"));
    }

    @Test
    void runtimeUnavailableApprovedVisionModelCannotBecomeDefault() {
        when(repository.listVisibleModels()).thenReturn(List.of(Map.of(
                "modelId", "AI-BUILDING-YOLOX-001",
                "modelName", "YOLOX 建筑病害模型",
                "modelVersion", "1.0.0",
                "modelType", "OBJECT_DETECTION",
                "mode", "REAL",
                "status", "APPROVED",
                "deploymentStage", "VALIDATING",
                "formalEvidenceEnabled", false,
                "providerCode", "FAST_API",
                "capabilityType", "VISION_INFERENCE")));
        when(fastApiClient.requireModelReady("AI-BUILDING-YOLOX-001", "REAL"))
                .thenThrow(new AiFastApiException("AI_MODEL_UNAVAILABLE", "模型未加载"));

        assertThatThrownBy(() -> service.requireSelectableVisionModel("AI-BUILDING-YOLOX-001"))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("运行时");
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuredDifyWorkflowIsSelectableWithoutFastApiRuntimeProbe() {
        when(repository.listVisibleModels()).thenReturn(List.of(Map.of(
                "modelId", "AI-DIFY-WORKFLOW-001",
                "modelName", "UrbanSafe Dify Image Analysis Workflow",
                "modelVersion", "image-analysis-v1.0.1",
                "modelType", "WORKFLOW",
                "mode", "REAL",
                "status", "APPROVED",
                "deploymentStage", "VALIDATING",
                "formalEvidenceEnabled", false,
                "providerCode", "DIFY",
                "capabilityType", "WORKFLOW")));
        when(difyProvider.providerCode()).thenReturn("DIFY");
        when(difyProvider.enabled()).thenReturn(true);
        when(difyProvider.configured()).thenReturn(true);
        when(difyProvider.supports(AiCapabilityType.WORKFLOW)).thenReturn(true);
        service = new AiModelCatalogService(repository, fastApiClient, List.of(difyProvider));

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) service.list().get("content");

        assertTrue((Boolean) content.get(0).get("runtimeReady"));
        assertTrue((Boolean) content.get(0).get("selectable"));
        assertEquals("DIFY", content.get(0).get("executionProvider"));
        assertEquals("WORKFLOW", content.get(0).get("capabilityType"));
        assertFalse((Boolean) content.get(0).get("formalEvidenceEnabled"));
    }
}
