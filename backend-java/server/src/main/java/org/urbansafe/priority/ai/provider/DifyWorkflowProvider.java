package org.urbansafe.priority.ai.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.urbansafe.priority.ai.client.DifyWorkflowClient;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.orchestration.AiCapabilityProvider;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationResult;
import org.urbansafe.priority.ai.workflow.AiWorkflowDefinition;
import org.urbansafe.priority.ai.workflow.AiWorkflowRegistry;

/** Dify Workflow 提供者，将工作流响应转换为项目统一结构。 */
public class DifyWorkflowProvider implements AiCapabilityProvider {

    public static final String PROVIDER_CODE = "DIFY";

    private final DifyWorkflowClient client;
    private final ObjectMapper objectMapper;
    private final DifyProperties properties;
    private final AiWorkflowRegistry workflowRegistry;

    public DifyWorkflowProvider(
            DifyWorkflowClient client,
            ObjectMapper objectMapper,
            DifyProperties properties,
            AiWorkflowRegistry workflowRegistry) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.workflowRegistry = workflowRegistry;
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
        return Set.of(
                AiCapabilityType.WORKFLOW,
                AiCapabilityType.VISION_INFERENCE,
                AiCapabilityType.TEXT_GENERATION);
    }

    @Override
    public AiOrchestrationResult execute(AiOrchestrationRequest request) {
        AiWorkflowDefinition workflow = workflowRegistry.requireByWorkflowCode(request.modelCode());
        JsonNode root = client.run(request);
        JsonNode data = root.path("data");
        String status = data.path("status").asText("");
        if (!"succeeded".equalsIgnoreCase(status)) {
            throw new AiProviderException(
                    AiErrorCodes.AI_WORKFLOW_FAILED, "Dify 工作流执行失败");
        }
        try {
            JsonNode outputs = data.path("outputs");
            JsonNode payloadNode = extractPayload(outputs);
            StructuredPayload payload = objectMapper.treeToValue(payloadNode, StructuredPayload.class);
            if (payload == null || payload.summary() == null || payload.summary().isBlank()) {
                throw new AiProviderException(
                        AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流返回数据缺少分析摘要");
            }
            validateVersionedContract(request, workflow, outputs, payloadNode, payload);
            String workflowRunId = root.path("workflow_run_id")
                    .asText(data.path("id").asText("unknown"));
            long durationMs = Math.max(0L,
                    Math.round(data.path("elapsed_time").asDouble(0d) * 1000d));
            String resultStatus = Boolean.FALSE.equals(payload.applicable())
                    ? "REJECTED" : "SUCCEEDED";
            return new AiOrchestrationResult(
                    request.requestId(),
                    PROVIDER_CODE,
                    workflow.workflowCode(),
                    firstNonBlank(payload.workflowVersion(),
                            firstNonBlank(payload.modelVersion(), workflow.currentVersion())),
                    request.capabilityType(),
                    resultStatus,
                    payload.summary(),
                    payload.detections(),
                    payload.riskSignals(),
                    payload.recommendations(),
                    payload.confidence(),
                    payload.warnings(),
                    "dify:" + workflowRunId,
                    durationMs);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流返回结构无法解析", ex);
        }
    }

    private void validateVersionedContract(
            AiOrchestrationRequest request,
            AiWorkflowDefinition workflow,
            JsonNode outputs,
            JsonNode payloadNode,
            StructuredPayload payload) {
        boolean version11 = workflow.currentVersion() != null
                && workflow.currentVersion().startsWith("image-analysis-v1.1");
        if (!version11) {
            return;
        }
        if (!"1.1".equals(payload.schemaVersion())) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 图片分析输出结构版本不匹配");
        }
        if (!workflow.workflowCode().equals(payload.workflowCode())) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流编号与登记不一致");
        }
        if (request.imageBytes() != null && request.imageBytes().length > 0) {
            JsonNode inputImage = outputs == null ? null : outputs.get("inputImage");
            if (inputImage == null || !inputImage.isObject()) {
                inputImage = payloadNode.get("inputImage");
            }
            validateInputImageEcho(inputImage);
        }
        if (payload.needsHumanReview() == null) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 输出缺少人工复核标记");
        }
    }

    private static void validateInputImageEcho(JsonNode inputImage) {
        if (inputImage == null || !inputImage.isObject()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流未确认接收巡检图片");
        }
        String type = inputImage.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        String mimeType = inputImage.path("mime_type")
                .asText(inputImage.path("mimeType").asText(""));
        if (!"image".equals(type)
                && !mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流返回的输入文件不是图片");
        }
    }

    private JsonNode extractPayload(JsonNode outputs) throws Exception {
        if (outputs == null || outputs.isMissingNode() || outputs.isNull()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流返回数据缺失");
        }
        JsonNode candidate = outputs.has("result") ? outputs.get("result")
                : outputs.has("analysis") ? outputs.get("analysis") : outputs;
        if (candidate.isTextual()) {
            return objectMapper.readTree(candidate.asText());
        }
        if (!candidate.isObject()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流返回结构不符合约定");
        }
        return candidate;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    /** Dify 工作流输出契约中未消费的附加字段（如 observations）应被容忍，避免破坏版本升级。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StructuredPayload(
            String schemaVersion,
            String workflowCode,
            String workflowVersion,
            Boolean applicable,
            String summary,
            List<AiOrchestrationResult.Detection> detections,
            List<AiOrchestrationResult.RiskSignal> riskSignals,
            List<String> recommendations,
            Double confidence,
            List<String> warnings,
            Boolean needsHumanReview,
            String modelVersion) {

        private StructuredPayload {
            detections = detections == null ? List.of() : List.copyOf(detections);
            riskSignals = riskSignals == null ? List.of() : List.copyOf(riskSignals);
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
