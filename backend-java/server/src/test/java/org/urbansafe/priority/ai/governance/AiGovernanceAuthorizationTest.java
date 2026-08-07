package org.urbansafe.priority.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/** 不创建 Spring Bean，避免测试辅助配置进入完整应用扫描。 */
class AiGovernanceAuthorizationTest {

    @Test
    void generatedApiMethodShouldRequireAdminRole() throws Exception {
        Method method = AiGovernanceController.class.getMethod("getAiGovernanceStatus");

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void responseShouldUseGeneratedContractAndRemainSanitized() {
        AiProviderStatusService service = mock(AiProviderStatusService.class);
        when(service.status()).thenReturn(new AiGovernanceStatus(
                Instant.parse("2026-07-31T12:00:00Z"),
                "LAST_7_DAYS",
                List.of(),
                AiProviderMetrics.empty(),
                0,
                "CONFIGURED 仅表示配置完整，未主动探测外部服务。",
                "仅用于运维和质量治理。"));
        AiGovernanceController controller = new AiGovernanceController(service);

        var response = controller.getAiGovernanceStatus();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getStatisticsWindow()).isEqualTo("LAST_7_DAYS");
        assertThat(response.getBody().getData().getProviders()).isEmpty();
        assertThat(response.getBody().toString())
                .doesNotContain("apiKey", "weightPath", "baseUrl", "rawResponse");
    }
}
