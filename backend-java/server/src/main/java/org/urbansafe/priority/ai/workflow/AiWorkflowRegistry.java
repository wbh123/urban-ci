package org.urbansafe.priority.ai.workflow;

import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.config.DifyWorkflowProperties;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

/** 将数据库登记与环境中的隔离密钥合并为可执行工作流。 */
@Service
public class AiWorkflowRegistry {

    private final AiWorkflowDefinitionRepository repository;
    private final DifyProperties properties;

    public AiWorkflowRegistry(AiWorkflowDefinitionRepository repository, DifyProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public AiWorkflowDefinition requireByWorkflowCode(String workflowCode) {
        AiWorkflowDefinition stored = repository.findByWorkflowOrModelCode(workflowCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI_WORKFLOW_NOT_FOUND", "人工智能工作流未登记"));
        if (!"DIFY".equals(stored.providerCode())) {
            return stored;
        }
        DifyWorkflowProperties configured = properties.resolveWorkflow(stored.configKey());
        return new AiWorkflowDefinition(
                stored.workflowCode(), stored.modelCode(), stored.displayName(), stored.providerCode(),
                stored.capabilityType(), stored.configKey(),
                firstNonBlank(configured == null ? null : configured.getVersion(), stored.currentVersion()),
                stored.inputSchemaVersion(), stored.outputSchemaVersion(), stored.enabled(),
                stored.qualityStatus(), stored.formalEvidenceEnabled(), stored.timeoutMs(),
                stored.maxAttempts(), stored.dataPolicy(),
                configured == null ? null : configured.getApiKey(),
                configured == null ? null : configured.getAppId(),
                configured != null && configured.configured());
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
