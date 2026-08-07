package org.urbansafe.priority.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.config.DifyWorkflowProperties;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

class AiWorkflowRegistryTest {

    @Test
    void shouldResolveDatabaseDefinitionAndMatchingDifyConfig() {
        AiWorkflowDefinitionRepository repository = mock(AiWorkflowDefinitionRepository.class);
        DifyProperties properties = properties();
        AiWorkflowDefinition stored = definition("DIFY-IMAGE-ANALYSIS-001", "image-analysis");
        when(repository.findByWorkflowOrModelCode("AI-DIFY-WORKFLOW-001"))
                .thenReturn(Optional.of(stored));
        AiWorkflowRegistry registry = new AiWorkflowRegistry(repository, properties);

        AiWorkflowDefinition resolved = registry.requireByWorkflowCode("AI-DIFY-WORKFLOW-001");

        assertThat(resolved.workflowCode()).isEqualTo("DIFY-IMAGE-ANALYSIS-001");
        assertThat(resolved.apiKey()).isEqualTo("image-key");
        assertThat(resolved.configured()).isTrue();
    }

    @Test
    void shouldRejectMissingWorkflowRegistration() {
        AiWorkflowDefinitionRepository repository = mock(AiWorkflowDefinitionRepository.class);
        when(repository.findByWorkflowOrModelCode("UNKNOWN")).thenReturn(Optional.empty());
        AiWorkflowRegistry registry = new AiWorkflowRegistry(repository, properties());

        assertThatThrownBy(() -> registry.requireByWorkflowCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldMarkDefinitionUnconfiguredWhenItsOwnKeyIsMissing() {
        AiWorkflowDefinitionRepository repository = mock(AiWorkflowDefinitionRepository.class);
        AiWorkflowDefinition stored = definition("DIFY-REPORT-DRAFT-001", "report-draft");
        when(repository.findByWorkflowOrModelCode("DIFY-REPORT-DRAFT-001"))
                .thenReturn(Optional.of(stored));
        AiWorkflowRegistry registry = new AiWorkflowRegistry(repository, properties());

        AiWorkflowDefinition resolved = registry.requireByWorkflowCode("DIFY-REPORT-DRAFT-001");

        assertThat(resolved.configured()).isFalse();
        assertThat(resolved.apiKey()).isNull();
    }

    private static DifyProperties properties() {
        DifyProperties properties = new DifyProperties();
        DifyWorkflowProperties image = new DifyWorkflowProperties();
        image.setApiKey("image-key");
        image.setAppId("image-app");
        image.setVersion("image-analysis-v1.1.0");
        properties.setWorkflows(Map.of("image-analysis", image));
        return properties;
    }

    private static AiWorkflowDefinition definition(String code, String configKey) {
        return new AiWorkflowDefinition(
                code,
                "AI-DIFY-WORKFLOW-001",
                "测试工作流",
                "DIFY",
                "WORKFLOW",
                configKey,
                "v1",
                "1.0",
                "1.0",
                true,
                "VALIDATING",
                false,
                300_000,
                3,
                Map.of(),
                null,
                null,
                false);
    }
}
