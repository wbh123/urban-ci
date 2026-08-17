package org.urbansafe.priority.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void acceptsVersion11RealDifyOutputWithObservationsField() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-real","data":{"status":"succeeded","elapsed_time":1.5,
                "outputs":{"inputImage":{"type":"image","mime_type":"image/jpeg"},
                "result":"{\\"schemaVersion\\":\\"1.1\\",\\"workflowCode\\":\\"DIFY-IMAGE-ANALYSIS-001\\",\\"workflowVersion\\":\\"image-analysis-v1.1.0\\",\\"applicable\\":true,\\"summary\\":\\"检测到裂缝\\",\\"observations\\":[\\"图像中可见一条裂缝\\"],\\"detections\\":[{\\"classCode\\":\\"CRACK\\",\\"className\\":\\"裂缝\\",\\"confidence\\":0.8,\\"boundingBox\\":null}],\\"riskSignals\\":[{\\"code\\":\\"VISIBLE_CRACK\\",\\"level\\":\\"MEDIUM\\",\\"description\\":\\"存在可见裂缝，可能影响结构安全\\",\\"confidence\\":0.8}],\\"recommendations\\":[\\"建议进行人工复核以确认裂缝的严重程度\\"],\\"warnings\\":[\\"信息限制\\"],\\"needsHumanReview\\":true,\\"confidence\\":0.8}"}}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.1.0"));

        AiStructuredResult result = provider.execute(request());

        assertEquals("SUCCEEDED", result.status());
        assertEquals("检测到裂缝", result.summary());
        assertEquals(1, result.detections().size());
        assertEquals("CRACK", result.detections().get(0).classCode());
        assertEquals(1, result.riskSignals().size());
        assertEquals("VISIBLE_CRACK", result.riskSignals().get(0).code());
        assertEquals(0.8d, result.confidence());
    }

    @Test
    void acceptsMarkdownFencedJsonStringFromReviewAssist() throws Exception {
        String payload = "{\"summary\":\"证据需人工复核\",\"needsHumanReview\":true,\"warnings\":[]}";
        JsonNode response = responseWithTextResult("run-fence", "```json\n" + payload + "\n```");
        DifyWorkflowProvider provider = provider(response, definition("review-assist-v1.2.0"));

        AiStructuredResult result = provider.execute(request());

        assertEquals("SUCCEEDED", result.status());
        assertEquals("证据需人工复核", result.summary());
    }

    @Test
    void acceptsOneLayerDoubleEncodedJsonStringFromReviewAssist() throws Exception {
        String payload = "{\"summary\":\"发现证据冲突\",\"needsHumanReview\":true,\"warnings\":[]}";
        String doubleEncoded = objectMapper.writeValueAsString(payload);
        JsonNode response = responseWithTextResult("run-double", doubleEncoded);
        DifyWorkflowProvider provider = provider(response, definition("review-assist-v1.2.0"));

        AiStructuredResult result = provider.execute(request());

        assertEquals("SUCCEEDED", result.status());
        assertEquals("发现证据冲突", result.summary());
    }

    @Test
    void rejectsNaturalLanguageWrappedJsonInsteadOfGuessingPayload() throws Exception {
        String value = "复核结果如下：{\"summary\":\"不应通过正则截取\"}";
        JsonNode response = responseWithTextResult("run-natural-language", value);
        DifyWorkflowProvider provider = provider(response, definition("review-assist-v1.2.0"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_INVALID_RESPONSE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("JSON 对象"));
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
    void rejectsFailedWorkflowAndPreservesDifyErrorAndRunId() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"run-2","data":{"status":"failed","error":"node failed"}}
                """);
        DifyWorkflowProvider provider = provider(response, definition("image-analysis-v1.0.1"));

        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.execute(request()));

        assertEquals(AiErrorCodes.AI_WORKFLOW_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("node failed"));
        assertTrue(exception.getMessage().contains("run-2"));
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

    private JsonNode responseWithTextResult(String runId, String result) {
        return objectMapper.valueToTree(Map.of(
                "workflow_run_id", runId,
                "data", Map.of(
                        "status", "succeeded",
                        "elapsed_time", 0.1,
                        "outputs", Map.of("result", result))));
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
