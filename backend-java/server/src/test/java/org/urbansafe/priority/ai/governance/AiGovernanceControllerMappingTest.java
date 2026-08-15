package org.urbansafe.priority.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiGovernanceControllerMappingTest {

    @Test
    void shouldExposeRuntimeStatusInApiDto() {
        AiProviderStatus status = new AiProviderStatus(
                "FAST_API",
                true,
                true,
                "CONFIGURED",
                "READY",
                "CONNECTED",
                List.of("VISION_INFERENCE"),
                List.of("VISION_INFERENCE"),
                AiProviderMetrics.empty());

        var dto = AiGovernanceController.toDto(status);

        assertThat(dto.getRuntimeStatus()).isEqualTo("READY");
        assertThat(dto.getConnectivityStatus()).isEqualTo("CONNECTED");
    }
}
