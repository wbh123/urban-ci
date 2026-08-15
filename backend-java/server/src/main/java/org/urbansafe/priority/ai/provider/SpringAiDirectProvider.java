package org.urbansafe.priority.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.urbansafe.priority.ai.client.SpringAiChatGateway;
import org.urbansafe.priority.ai.config.SpringAiProviderProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;

/** Spring AI DeepSeek 文本生成提供者。 */
public class SpringAiDirectProvider implements AiCapabilityProvider {

    public static final String PROVIDER_CODE = "SPRING_AI";

    private final SpringAiChatGateway gateway;
    private final ObjectMapper objectMapper;
    private final SpringAiProviderProperties properties;

    public SpringAiDirectProvider(
            SpringAiChatGateway gateway,
            ObjectMapper objectMapper,
            SpringAiProviderProperties properties) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean enabled() {
        return properties.isEnabled();
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    public Set<AiCapabilityType> capabilities() {
        return Set.of(AiCapabilityType.TEXT_GENERATION);
    }

    @Override
    public AiOrchestrationResult execute(AiOrchestrationRequest request) {
        long started = System.nanoTime();
        try {
            String raw = gateway.generate(request);
            if (raw == null || raw.isBlank()) {
                throw new AiProviderException(
                        AiErrorCodes.AI_INVALID_RESPONSE, "DeepSeek 返回空响应");
            }
            String json = stripCodeFence(raw);
            JsonNode node = objectMapper.readTree(json);
            StructuredPayload payload = objectMapper.treeToValue(node, StructuredPayload.class);
            if (payload == null || payload.summary() == null || payload.summary().isBlank()) {
                throw new AiProviderException(
                        AiErrorCodes.AI_INVALID_RESPONSE, "DeepSeek 返回数据缺少分析摘要");
            }
            long durationMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            String actualModel = properties.getModel();
            return new AiOrchestrationResult(
                    request.requestId(),
                    PROVIDER_CODE,
                    actualModel,
                    firstNonBlank(payload.modelVersion(), actualModel),
                    request.capabilityType(),
                    "SUCCEEDED",
                    payload.summary(),
                    payload.detections(),
                    payload.riskSignals(),
                    payload.recommendations(),
                    payload.confidence(),
                    payload.warnings(),
                    "spring-ai:sha256:" + digest(json),
                    durationMs);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "DeepSeek 响应无法解析", ex);
        }
    }

    private static String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, lastFence).trim();
    }

    private static String digest(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash).substring(0, 24);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record StructuredPayload(
            String summary,
            List<AiOrchestrationResult.Detection> detections,
            List<AiOrchestrationResult.RiskSignal> riskSignals,
            List<String> recommendations,
            Double confidence,
            List<String> warnings,
            String modelVersion) {

        private StructuredPayload {
            detections = detections == null ? List.of() : List.copyOf(detections);
            riskSignals = riskSignals == null ? List.of() : List.copyOf(riskSignals);
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
