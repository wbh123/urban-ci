package org.urbansafe.priority.ai.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final int MAX_FAILURE_DETAIL_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(DifyWorkflowProvider.class);

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
        logDifyResponse(workflow.workflowCode(), root, data, status);
        if (!"succeeded".equalsIgnoreCase(status)) {
            throw new AiProviderException(
                    AiErrorCodes.AI_WORKFLOW_FAILED, workflowFailureMessage(root, data, status));
        }
        try {
            JsonNode outputs = data.path("outputs");
            JsonNode payloadNode = extractPayload(outputs);
            StructuredPayload payload = objectMapper.treeToValue(payloadNode, StructuredPayload.class);
            if (payload == null || payload.summary() == null || payload.summary().isBlank()) {
                throw new AiProviderException(
                        AiErrorCodes.AI_INVALID_RESPONSE, "DIFY_OUTPUT_SUMMARY_MISSING：Dify 工作流返回数据缺少分析摘要");
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
                    mergedRecommendations(payload),
                    payload.confidence(),
                    mergedWarnings(payload),
                    "dify:" + workflowRunId,
                    durationMs);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 工作流返回结构无法解析", ex);
        }
    }

    private static String workflowFailureMessage(JsonNode root, JsonNode data, String status) {
        String workflowRunId = root.path("workflow_run_id")
                .asText(data.path("id").asText("unknown"));
        String error = data.path("error").asText(root.path("error").asText(""));
        String normalizedError = error == null ? "" : error.replaceAll("\\s+", " ").trim();
        if (normalizedError.length() > MAX_FAILURE_DETAIL_LENGTH) {
            normalizedError = normalizedError.substring(0, MAX_FAILURE_DETAIL_LENGTH) + "…";
        }
        if (normalizedError.isBlank()) {
            normalizedError = "Dify 未返回具体错误信息";
        }
        String normalizedStatus = status == null || status.isBlank() ? "unknown" : status;
        return "Dify 工作流执行失败（status=" + normalizedStatus
                + ", runId=" + workflowRunId + "）：" + normalizedError;
    }

    /** 安全诊断日志：只记录结构元信息，绝不打印 API Key 或业务敏感 JSON。 */
    private static void logDifyResponse(
            String workflowCode, JsonNode root, JsonNode data, String status) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String workflowRunId = root.path("workflow_run_id").asText(data.path("id").asText("unknown"));
        JsonNode outputs = data.path("outputs");
        List<String> keys = new ArrayList<>();
        if (outputs != null && outputs.isObject()) {
            outputs.fieldNames().forEachRemaining(keys::add);
        }
        JsonNode result = outputs != null && outputs.has("result") ? outputs.get("result") : outputs;
        String resultType = result == null ? "NULL"
                : result.isTextual() ? "STRING"
                : result.isObject() ? "OBJECT"
                : result.isArray() ? "ARRAY" : "OTHER";
        int resultLength = result == null ? 0
                : result.isTextual() ? result.asText().length() : result.toString().length();
        String error = data.path("error").asText("");
        log.debug("Dify workflow diagnostic workflowCode={} runId={} status={} outputsKeys=[{}] resultType={} resultLength={} error={}",
                workflowCode, workflowRunId, status, String.join(",", keys), resultType, resultLength, error);
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

    /**
     * 提取 Dify outputs 中的业务 JSON。
     *
     * <p>允许三种稳定形态：对象、纯 JSON 字符串、完整 Markdown JSON 围栏；另外兼容最多一层
     * JSON 字符串二次编码。禁止从自然语言中用正则截取 JSON，避免把模型解释文本误当正式结构。
     */
    private JsonNode extractPayload(JsonNode outputs) {
        if (outputs == null || outputs.isMissingNode() || outputs.isNull()) {
            throw invalidPayload("DIFY_OUTPUT_MISSING：Dify 工作流返回数据缺失");
        }
        JsonNode candidate = outputs.has("result") ? outputs.get("result")
                : outputs.has("analysis") ? outputs.get("analysis") : outputs;
        if (candidate != null && candidate.isObject()) {
            return candidate;
        }
        if (candidate == null || !candidate.isTextual()) {
            throw invalidPayload("DIFY_OUTPUT_NOT_JSON_OBJECT：Dify 工作流返回结构不是 JSON 对象");
        }

        String text = stripCompleteJsonFence(candidate.asText());
        JsonNode parsed = parseSingleJsonValue(text);
        if (parsed.isObject()) {
            return parsed;
        }
        if (parsed.isTextual()) {
            JsonNode second = parseSingleJsonValue(stripCompleteJsonFence(parsed.asText()));
            if (second.isObject()) {
                return second;
            }
        }
        throw invalidPayload("DIFY_OUTPUT_NOT_JSON_OBJECT：Dify 工作流 result 不是 JSON 对象");
    }

    private JsonNode parseSingleJsonValue(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isBlank()) {
            throw invalidPayload("DIFY_OUTPUT_NOT_JSON_OBJECT：Dify 工作流 result 为空");
        }
        try (JsonParser parser = objectMapper.createParser(value)) {
            JsonNode parsed = objectMapper.readTree(parser);
            if (parsed == null || parser.nextToken() != null) {
                throw invalidPayload("DIFY_OUTPUT_NOT_JSON_OBJECT：Dify 工作流 result 必须是单一完整 JSON 值");
            }
            return parsed;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE,
                    "DIFY_OUTPUT_NOT_JSON_OBJECT：Dify 工作流 result 不是完整 JSON 对象", ex);
        } catch (IOException ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE,
                    "DIFY_OUTPUT_NOT_JSON_OBJECT：读取 Dify 工作流 result 失败", ex);
        }
    }

    private static String stripCompleteJsonFence(String text) {
        String value = text == null ? "" : text.trim();
        if (!value.startsWith("```") || !value.endsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        if (firstLineEnd < 0) {
            return value;
        }
        String fenceLabel = value.substring(3, firstLineEnd).trim();
        if (!fenceLabel.isBlank() && !"json".equalsIgnoreCase(fenceLabel)) {
            return value;
        }
        return value.substring(firstLineEnd + 1, value.length() - 3).trim();
    }

    private static AiProviderException invalidPayload(String message) {
        return new AiProviderException(AiErrorCodes.AI_INVALID_RESPONSE, message);
    }

    private static List<String> mergedRecommendations(StructuredPayload payload) {
        List<String> result = new ArrayList<>(payload.recommendations());
        for (String request : payload.reshootRequests()) {
            if (request != null && !request.isBlank() && !result.contains(request)) {
                result.add(request);
            }
        }
        for (String question : payload.reviewQuestions()) {
            if (question != null && !question.isBlank() && !result.contains(question)) {
                result.add(question);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> mergedWarnings(StructuredPayload payload) {
        List<String> result = new ArrayList<>(payload.warnings());
        for (String conflict : payload.evidenceConflicts()) {
            addPrefixed(result, "证据冲突：", conflict);
        }
        for (String missing : payload.missingFields()) {
            addPrefixed(result, "缺失字段：", missing);
        }
        return List.copyOf(result);
    }

    private static void addPrefixed(List<String> target, String prefix, String value) {
        if (value == null || value.isBlank()) return;
        String item = prefix + value;
        if (!target.contains(item)) target.add(item);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    /** Dify 工作流输出契约中未消费的附加字段应被容忍，避免破坏版本升级。 */
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
            String modelVersion,
            List<String> evidenceAgreements,
            List<String> evidenceConflicts,
            List<String> missingFields,
            List<String> reshootRequests,
            List<String> reviewQuestions) {

        private StructuredPayload {
            detections = detections == null ? List.of() : List.copyOf(detections);
            riskSignals = riskSignals == null ? List.of() : List.copyOf(riskSignals);
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            evidenceAgreements = evidenceAgreements == null ? List.of() : List.copyOf(evidenceAgreements);
            evidenceConflicts = evidenceConflicts == null ? List.of() : List.copyOf(evidenceConflicts);
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
            reshootRequests = reshootRequests == null ? List.of() : List.copyOf(reshootRequests);
            reviewQuestions = reviewQuestions == null ? List.of() : List.copyOf(reviewQuestions);
        }
    }
}
