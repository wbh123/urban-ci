package org.urbansafe.priority.ai.governance;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.urbansafe.priority.ai.client.AiFastApiClient;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.config.DifyWorkflowProperties;
import org.urbansafe.priority.ai.config.SpringAiProviderProperties;

/**
 * 提供者真实连接探测（带缓存，避免频繁调用消耗 API/配额）。
 *
 * <p>状态词汇：READY / DEGRADED / UNCONFIGURED / AUTH_ERROR / UNAVAILABLE / DISABLED。
 * 探测结果缓存 5 分钟；DeepSeek 用 /models 探测，Dify 用只读 workflow logs 探测，
 * 均不执行正式推理/工作流。
 */
@Service
public class AiProviderProbeService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderProbeService.class);
    private static final Duration PROBE_TTL = Duration.ofMinutes(5);

    private final AiFastApiClient fastApiClient;
    private final SpringAiProviderProperties springAiProperties;
    private final DifyProperties difyProperties;
    private final RestClient.Builder restClientBuilder;
    private final Map<String, ProbeResult> cache = new ConcurrentHashMap<>();

    public AiProviderProbeService(
            AiFastApiClient fastApiClient,
            SpringAiProviderProperties springAiProperties,
            DifyProperties difyProperties,
            RestClient.Builder restClientBuilder) {
        this.fastApiClient = fastApiClient;
        this.springAiProperties = springAiProperties;
        this.difyProperties = difyProperties;
        this.restClientBuilder = restClientBuilder;
    }

    public ProbeResult probe(String providerCode) {
        return switch (providerCode) {
            case "FAST_API" -> probeFastApi();
            case "SPRING_AI" -> probeDeepSeek();
            case "DIFY" -> probeDify();
            default -> new ProbeResult("UNKNOWN", Instant.now());
        };
    }

    private ProbeResult probeFastApi() {
        ProbeResult cached = cached("FAST_API");
        if (cached != null) {
            return cached;
        }
        try {
            fastApiClient.requireModelReady("AI-VISION-LOCAL-001", "REAL");
            return cache("FAST_API", new ProbeResult("READY", Instant.now()));
        } catch (Exception ex) {
            log.debug("FastAPI 探测失败：{}", ex.getMessage());
            return cache("FAST_API", new ProbeResult("DEGRADED", Instant.now()));
        }
    }

    private ProbeResult probeDeepSeek() {
        ProbeResult cached = cached("SPRING_AI");
        if (cached != null) {
            return cached;
        }
        if (!springAiProperties.configured()) {
            return cache("SPRING_AI", new ProbeResult("UNCONFIGURED", Instant.now()));
        }
        try {
            RestClient client = restClientBuilder.clone()
                    .baseUrl(springAiProperties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + springAiProperties.getApiKey())
                    .build();
            client.get().uri("/models").retrieve().toBodilessEntity();
            return cache("SPRING_AI", new ProbeResult("READY", Instant.now()));
        } catch (RestClientResponseException ex) {
            return cache("SPRING_AI", new ProbeResult(runtimeStatus(ex.getStatusCode()), Instant.now()));
        } catch (Exception ex) {
            log.debug("DeepSeek 探测失败：{}", ex.getMessage());
            return cache("SPRING_AI", new ProbeResult("DEGRADED", Instant.now()));
        }
    }

    private ProbeResult probeDify() {
        ProbeResult cached = cached("DIFY");
        if (cached != null) {
            return cached;
        }
        if (!difyProperties.isEnabled()) {
            return cache("DIFY", new ProbeResult("DISABLED", Instant.now()));
        }
        DifyWorkflowProperties workflow = firstConfiguredWorkflow();
        if (workflow == null || difyProperties.getBaseUrl() == null || difyProperties.getBaseUrl().isBlank()) {
            return cache("DIFY", new ProbeResult("UNCONFIGURED", Instant.now()));
        }
        try {
            RestClient client = restClientBuilder.clone()
                    .baseUrl(difyProperties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + workflow.getApiKey())
                    .build();
            client.get()
                    .uri("/workflows/logs?page=1&limit=1")
                    .retrieve()
                    .toBodilessEntity();
            return cache("DIFY", new ProbeResult("READY", Instant.now()));
        } catch (RestClientResponseException ex) {
            return cache("DIFY", new ProbeResult(runtimeStatus(ex.getStatusCode()), Instant.now()));
        } catch (Exception ex) {
            log.debug("Dify Cloud 探测失败：{}", ex.getMessage());
            return cache("DIFY", new ProbeResult("DEGRADED", Instant.now()));
        }
    }

    private DifyWorkflowProperties firstConfiguredWorkflow() {
        for (DifyWorkflowProperties workflow : difyProperties.getWorkflows().values()) {
            if (workflow != null && workflow.configured()) {
                return workflow;
            }
        }
        DifyWorkflowProperties legacy = difyProperties.resolveWorkflow("image-analysis");
        return legacy != null && legacy.configured() ? legacy : null;
    }

    private static String runtimeStatus(HttpStatusCode status) {
        int code = status.value();
        if (code == 401 || code == 403) {
            return "AUTH_ERROR";
        }
        if (code == 408 || code == 429 || code >= 500) {
            return "DEGRADED";
        }
        return "UNAVAILABLE";
    }

    private ProbeResult cached(String providerCode) {
        ProbeResult result = cache.get(providerCode);
        if (result == null) {
            return null;
        }
        return withinTtl(result, Instant.now()) ? result : null;
    }

    static boolean withinTtl(ProbeResult result, Instant now) {
        if (result == null || result.probedAt() == null || now == null || result.probedAt().isAfter(now)) {
            return false;
        }
        return Duration.between(result.probedAt(), now).compareTo(PROBE_TTL) < 0;
    }

    private ProbeResult cache(String providerCode, ProbeResult result) {
        cache.put(providerCode, result);
        return result;
    }

    public record ProbeResult(String runtimeStatus, Instant probedAt) {
    }
}
