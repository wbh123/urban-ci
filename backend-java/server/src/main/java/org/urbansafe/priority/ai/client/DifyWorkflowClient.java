package org.urbansafe.priority.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;

/** Dify 工作流客户端边界。 */
@FunctionalInterface
public interface DifyWorkflowClient {
    JsonNode run(AiOrchestrationRequest request);
}
