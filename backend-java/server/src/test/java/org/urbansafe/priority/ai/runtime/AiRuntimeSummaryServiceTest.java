package org.urbansafe.priority.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.governance.AiProviderProbeService;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;

class AiRuntimeSummaryServiceTest {

    @Test
    void returnsOnlyBusinessSafeServiceLabelsAndFallbackPolicy() {
        AiCapabilityProvider vision = provider("FAST_API", AiCapabilityType.VISION_INFERENCE);
        AiCapabilityProvider workflow = provider("DIFY", AiCapabilityType.WORKFLOW);
        AiCapabilityProvider spring = provider("SPRING_AI", AiCapabilityType.TEXT_GENERATION);
        AiProviderProbeService probeService = mock(AiProviderProbeService.class);
        when(probeService.probe("FAST_API"))
                .thenReturn(new AiProviderProbeService.ProbeResult("READY", Instant.now()));
        when(probeService.probe("DIFY"))
                .thenReturn(new AiProviderProbeService.ProbeResult("UNAVAILABLE", Instant.now()));
        when(probeService.probe("SPRING_AI"))
                .thenReturn(new AiProviderProbeService.ProbeResult("READY", Instant.now()));

        AiRuntimeSummaryService service = new AiRuntimeSummaryService(
                List.of(vision, workflow, spring), probeService);
        Map<String, Object> summary = service.summary();

        assertEquals("DEGRADED", summary.get("state"));
        assertEquals("Dify 优先 / 本地兜底", summary.get("policy"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> services = (List<Map<String, Object>>) summary.get("services");
        assertEquals(List.of("本地视觉", "智能工作流", "本地编排", "知识服务"),
                services.stream().map(item -> String.valueOf(item.get("label"))).toList());
        assertEquals("不可用", services.get(1).get("status"));
        assertFalse(summary.toString().contains("FAST_API"));
    }

    private static AiCapabilityProvider provider(String code, AiCapabilityType capability) {
        AiCapabilityProvider provider = mock(AiCapabilityProvider.class);
        when(provider.providerCode()).thenReturn(code);
        when(provider.enabled()).thenReturn(true);
        when(provider.configured()).thenReturn(true);
        when(provider.capabilities()).thenReturn(Set.of(capability));
        return provider;
    }
}
