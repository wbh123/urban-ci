package org.urbansafe.priority.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.workflow.AiWorkflowDefinition;
import org.urbansafe.priority.ai.workflow.AiWorkflowRegistry;

class DifyReviewAssistProviderContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsPublishedReviewAssistOutputIntoUnifiedRecommendations() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"workflow_run_id":"review-run-1","data":{"status":"succeeded","elapsed_time":0.2,
                "outputs":{"result":"{\\"schemaVersion\\":\\"1.0\\",\\"workflowCode\\":\\"DIFY-REVIEW-ASSIST-001\\",\\"workflowVersion\\":\\"review-assist-v1.0.0\\",\\"summary\\":\\"存在一项证据冲突，建议专家复核\\",\\"evidenceAgreements\\":[\\"楼栋编号一致\\"],\\"evidenceConflicts\\":[\\"裂缝位置描述不一致\\"],\\"missingFields\\":[\\"裂缝宽度\\"],\\"reshootRequests\\":[\\"补拍裂缝近景\\"],\\"reviewQuestions\\":[\\"是否存在持续扩展\\"],\\"warnings\\":[\\"仅供辅助\\"],\\"needsHumanReview\\":true}"}}}
                """);
        AiWorkflowRegistry registry = mock(AiWorkflowRegistry.class);
        when(registry.requireByWorkflowCode("DIFY-REVIEW-ASSIST-001")).thenReturn(definition());
        DifyProperties properties = new DifyProperties();
        properties.setEnabled(true);
        DifyWorkflowProvider provider = new DifyWorkflowProvider(
                request -> response, objectMapper, properties, registry);

        AiStructuredResult result = provider.execute(new AiOrchestrationRequest(
                "request-1", AiCapabilityType.WORKFLOW, "DIFY", "DIFY-REVIEW-ASSIST-001", "REAL",
                null, null, "专业复核辅助", Map.of()));

        assertThat(result.summary()).contains("证据冲突");
        assertThat(result.recommendations()).contains("补拍裂缝近景", "是否存在持续扩展");
        assertThat(result.warnings()).contains("仅供辅助");
    }

    private static AiWorkflowDefinition definition() {
        return new AiWorkflowDefinition(
                "DIFY-REVIEW-ASSIST-001", null, "巡检复核辅助",
                "DIFY", "WORKFLOW", "review-assist", "review-assist-v1.0.0", "1.0", "1.0",
                true, "VALIDATING", false, 180000, 2, Map.of(), "key", "app", true);
    }
}
