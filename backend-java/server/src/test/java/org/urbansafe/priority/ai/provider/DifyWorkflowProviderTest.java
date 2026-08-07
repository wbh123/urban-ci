package org.urbansafe.priority.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.workflow.AiWorkflowDefinition;
import org.urbansafe.priority.ai.workflow.AiWorkflowRegistry;

class DifyWorkflowProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void convertsLegacyImageWorkflowResponseDuringCompatibilityRelease() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-1","data":{"status":"succeeded","elapsed_time":0.25,
                "outputs":{"result":{"summary":"发现一处疑似裂缝","modelVersion":"workflow-v1",
                "confidence":0.81,"recommendations":["安排人工复核"],"warnings":["辅助结果"]}}}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.0.1"));

        AiStructuredResult result = provider.execute(request());

        assertEquals("DIFY", result.providerCode());
        assertEquals("发现一处疑似裂缝", result.summary());
        assertEquals(0.81d, result.confidence());
        assertEquals("dify:run-1", result.rawResponseReference());
    }

    @Test
    void acceptsVersion11ContractWithConfirmedImageEcho() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-11","data":{"status":"succeeded","elapsed_time":0.1,
                "outputs":{"inputImage":{"type":"image","mime_type":"image/jpeg"},
                "result":{"schemaVersion":"1.1","workflowCode":"DIFY-IMAGE-ANALYSIS-001",
                "workflowVersion":"image-analysis-v1.1.0","applicable":true,
                "summary":"发现疑似裂缝","detections":[],"riskSignals":[],
                "recommendations":["人工复核"],"warnings":[],"needsHumanReview":true,
                "confidence":0.8}}}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.1.0"));

        AiStructuredResult result = provider.execute(request());

        assertEquals("SUCCEEDED", result.status());
        assertEquals("image-analysis-v1.1.0", result.modelVersion());
    }

    @Test
    void version11ShouldRejectMissingImageEcho() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-12","data":{"status":"succeeded","outputs":{"result":{
                "schemaVersion":"1.1","workflowCode":"DIFY-IMAGE-ANALYSIS-001",
                "workflowVersion":"image-analysis-v1.1.0","applicable":true,
                "summary":"疑似病害","needsHumanReview":true,"confidence":0.7}}}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.1.0"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_INVALID_RESPONSE, exception.getErrorCode());
    }

    @Test
    void rejectsFailedWorkflow() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-2","data":{"status":"failed","error":"node failed"}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.0.1"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_WORKFLOW_FAILED, exception.getErrorCode());
    }

    @Test
    void preservesTimeoutErrorCodeFromClient() {
        AiWorkflowRegistry registry = mock(AiWorkflowRegistry.class);
        when(registry.requireByWorkflowCode("AI-DIFY-WORKFLOW-001"))
                .thenReturn(definition("image-analysis-v1.0.1"));
        DifyWorkflowProvider provider = new DifyWorkflowProvider(
                request -> { throw new AiProviderException(
                        AiErrorCodes.AI_PROVIDER_TIMEOUT, "Dify 工作流调用超时"); },
                objectMapper, configuredProperties(), registry);

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_PROVIDER_TIMEOUT, exception.getErrorCode());
    }

    @Test
    void rejectsMissingStructuredOutputs() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-3","data":{"status":"succeeded","outputs":null}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.0.1"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_INVALID_RESPONSE, exception.getErrorCode());
    }

    private DifyWorkflowProvider provider(JsonNode response, AiWorkflowDefinition definition) {
        AiWorkflowRegistry registry = mock(AiWorkflowRegistry.class);
        when(registry.requireByWorkflowCode("AI-DIFY-WORKFLOW-001")).thenReturn(definition);
        return new DifyWorkflowProvider(request -> response, objectMapper, configuredProperties(), registry);
    }

    private static AiOrchestrationRequest request() {
        return new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "AI-DIFY-WORKFLOW-001", "REAL",
                new byte[]{1}, "image/jpeg", "分析建筑表观病害", Map.of());
    }

    private static AiWorkflowDefinition definition(String version) {
        return new AiWorkflowDefinition(
                "DIFY-IMAGE-ANALYSIS-001", "AI-DIFY-WORKFLOW-001", "建筑病害分析",
                "DIFY", "WORKFLOW", "image-analysis", version, "1.1", "1.1",
                true, "VALIDATING", false, 300000, 3, Map.of(), "key", "app", true);
    }

    private static DifyProperties configuredProperties() {
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setWorkflowId("workflow-1");
        properties.setWorkflowVersion("image-analysis-v1.0.1");
        return properties;
    }
}
