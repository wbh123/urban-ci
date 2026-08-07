package org.urbansafe.priority.ai.client;

import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;

/** Spring AI ChatClient 的项目适配边界。 */
@FunctionalInterface
public interface SpringAiChatGateway {
    String generate(AiOrchestrationRequest request);
}
