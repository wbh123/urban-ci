package org.urbansafe.priority.ai.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AiAgentTraceTest {

    @AfterEach
    void tearDown() {
        AiAgentTrace.end();
    }

    @Test
    void beginShouldPreserveContextBoundByAsyncExecutor() {
        UUID sourceInferenceId = UUID.randomUUID();
        AiAgentTrace.bindContext(Map.of("sourceInferenceId", sourceInferenceId.toString()));
        AiAgentExecution execution = new AiAgentExecution(
                UUID.randomUUID(), "AI_INFERENCE", UUID.randomUUID(),
                "自动研判", UUID.randomUUID(), "tester");

        AiAgentTrace.begin(execution);

        assertThat(AiAgentTrace.current()).isSameAs(execution);
        assertThat(AiAgentTrace.contextValue("sourceInferenceId"))
                .isEqualTo(sourceInferenceId.toString());
    }

    @Test
    void endShouldClearExecutionAndContext() {
        AiAgentTrace.begin(
                new AiAgentExecution(
                        UUID.randomUUID(), "BUILDING", UUID.randomUUID(),
                        "研判", UUID.randomUUID(), "tester"),
                Map.of("sourceInferenceId", UUID.randomUUID().toString()));

        AiAgentTrace.end();

        assertThat(AiAgentTrace.current()).isNull();
        assertThat(AiAgentTrace.contextValue("sourceInferenceId")).isNull();
    }
}
