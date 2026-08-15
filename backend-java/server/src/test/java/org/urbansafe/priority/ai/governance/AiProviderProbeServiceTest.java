package org.urbansafe.priority.ai.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.config.DifyWorkflowProperties;
import org.urbansafe.priority.ai.config.SpringAiProviderProperties;

class AiProviderProbeServiceTest {

    @Test
    void ttlUsesWallClockInstantConsistently() {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        AiProviderProbeService.ProbeResult fresh =
                new AiProviderProbeService.ProbeResult("READY", now.minus(Duration.ofMinutes(4)));
        AiProviderProbeService.ProbeResult expired =
                new AiProviderProbeService.ProbeResult("READY", now.minus(Duration.ofMinutes(6)));

        assertThat(AiProviderProbeService.withinTtl(fresh, now)).isTrue();
        assertThat(AiProviderProbeService.withinTtl(expired, now)).isFalse();
    }

    @Test
    void difyReadyRequiresAuthenticatedCloudProbe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        DifyProperties dify = difyProperties("workflow-key");
        server.expect(requestTo("https://api.dify.ai/v1/workflows/logs?page=1&limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer workflow-key"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        AiProviderProbeService service = new AiProviderProbeService(
                mock(AiFastApiClient.class),
                new SpringAiProviderProperties(),
                dify,
                builder);

        assertThat(service.probe("DIFY").runtimeStatus()).isEqualTo("READY");
        server.verify();
    }

    @Test
    void difyWithoutWorkflowCredentialIsUnconfigured() {
        DifyProperties dify = new DifyProperties();
        dify.setEnabled(true);
        dify.setBaseUrl("https://api.dify.ai/v1");

        AiProviderProbeService service = new AiProviderProbeService(
                mock(AiFastApiClient.class),
                new SpringAiProviderProperties(),
                dify,
                RestClient.builder());

        assertThat(service.probe("DIFY").runtimeStatus()).isEqualTo("UNCONFIGURED");
    }

    private static DifyProperties difyProperties(String apiKey) {
        DifyWorkflowProperties workflow = new DifyWorkflowProperties();
        workflow.setApiKey(apiKey);
        workflow.setAppId("review-assist");
        workflow.setVersion("v1");

        DifyProperties dify = new DifyProperties();
        dify.setEnabled(true);
        dify.setBaseUrl("https://api.dify.ai/v1");
        dify.setWorkflows(Map.of("review-assist", workflow));
        return dify;
    }
}
