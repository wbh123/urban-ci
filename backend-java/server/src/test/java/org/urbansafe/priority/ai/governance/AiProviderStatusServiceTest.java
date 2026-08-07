package org.urbansafe.priority.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationProperties;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;

class AiProviderStatusServiceTest {

    @Test
    void shouldExposeConfigurationDefaultsAndMetricsWithoutConnectivityClaims() {
        AiGovernanceRepository repository = mock(AiGovernanceRepository.class);
        when(repository.providerMetrics(7)).thenReturn(Map.of(
                "FAST_API", new AiProviderMetrics(10, 8, 2, 5, 3, 420, 80d),
                "LEGACY", new AiProviderMetrics(2, 1, 1, 0, 1, 100, 50d)));

        AiOrchestrationProperties properties = new AiOrchestrationProperties();
        properties.setDefaultVisionProvider("FAST_API");
        properties.setDefaultWorkflowProvider("DIFY");

        AiProviderStatusService service = new AiProviderStatusService(
                List.of(
                        provider("FAST_API", true, true, Set.of(AiCapabilityType.VISION_INFERENCE)),
                        provider("DIFY", false, false, Set.of(AiCapabilityType.WORKFLOW))),
                properties,
                repository);

        AiGovernanceStatus status = service.status();

        assertThat(status.statisticsWindow()).isEqualTo("LAST_7_DAYS");
        assertThat(status.total7d().totalTasks()).isEqualTo(12);
        assertThat(status.unassignedLegacyTasks7d()).isEqualTo(2);
        assertThat(status.providers()).extracting(AiProviderStatus::providerCode)
                .containsExactly("DIFY", "FAST_API");

        AiProviderStatus fastApi = status.providers().stream()
                .filter(item -> item.providerCode().equals("FAST_API"))
                .findFirst()
                .orElseThrow();
        assertThat(fastApi.configurationStatus()).isEqualTo("CONFIGURED");
        assertThat(fastApi.connectivityStatus()).isEqualTo("NOT_PROBED");
        assertThat(fastApi.defaultFor()).containsExactly("VISION_INFERENCE");
        assertThat(fastApi.metrics7d().successRate()).isEqualTo(80d);

        AiProviderStatus dify = status.providers().stream()
                .filter(item -> item.providerCode().equals("DIFY"))
                .findFirst()
                .orElseThrow();
        assertThat(dify.configurationStatus()).isEqualTo("DISABLED");
        assertThat(status.healthSemantics()).contains("未主动调用外部服务");
    }

    private static AiCapabilityProvider provider(
            String code,
            boolean enabled,
            boolean configured,
            Set<AiCapabilityType> capabilities) {
        return new AiCapabilityProvider() {
            @Override
            public String providerCode() {
                return code;
            }

            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public boolean configured() {
                return configured;
            }

            @Override
            public Set<AiCapabilityType> capabilities() {
                return capabilities;
            }

            @Override
            public AiStructuredResult execute(AiOrchestrationRequest request) {
                throw new UnsupportedOperationException("not used");
            }
        };
    }
}
