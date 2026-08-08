package org.urbansafe.priority.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.urbansafe.priority.ai.config.AiInferenceProperties;

/**
 * 调用 FastAPI 内部模型目录、图片质量预检与推理接口的 HTTP 客户端。
 *
 * <p>负责 Multipart 图片发送、真实模型 CUDA 就绪预检、超时与错误转换、
 * 请求编号、模式和模型身份一致性校验。不直接写业务数据库，也不直接访问 MinIO。
 */
public class AiFastApiClient {

    private static final double EPSILON = 1e-9;
    private static final String CUDA_EXECUTION_PROVIDER = "CUDAExecutionProvider";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiInferenceProperties properties;

    public AiFastApiClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            AiInferenceProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /** 调用 FastAPI 执行一次推理，并校验响应仍是请求的模式和模型。 */
    public AiInferenceResponse infer(byte[] imageBytes, Map<String, Object> metadata, String requestId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ImageResource(imageBytes));
        try {
            parts.add("metadata", objectMapper.writeValueAsString(metadata));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "推理元数据序列化失败", ex);
        }

        String body;
        try {
            body = restClient.post()
                    .uri("/internal/api/v1/ai/inferences")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException ex) {
            throw mapNetworkError(ex);
        } catch (RestClientResponseException ex) {
            throw mapErrorResponse(ex);
        } catch (RuntimeException ex) {
            throw mapRuntimeError(ex);
        }

        return parseSuccess(body, requestId, metadata);
    }

    /** 在调用在线视觉工作流前执行无需模型权重的本地图片质量预检。 */
    public AiImageQualityResponse analyzeImageQuality(byte[] imageBytes, String requestId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ImageResource(imageBytes));
        parts.add("requestId", requestId);

        String body;
        try {
            body = restClient.post()
                    .uri("/internal/api/v1/ai/image-quality")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException ex) {
            throw mapNetworkError(ex);
        } catch (RestClientResponseException ex) {
            throw mapErrorResponse(ex);
        } catch (RuntimeException ex) {
            throw mapRuntimeError(ex);
        }

        return parseImageQuality(body, requestId);
    }

    /**
     * 按模型编号查询 FastAPI 实际运行时身份。
     * REAL 模型必须已批准、就绪，并且实际执行后端只能是 CUDAExecutionProvider。
     */
    public AiRuntimeModelInfo requireModelReady(String expectedModelId, String expectedMode) {
        String body;
        try {
            body = restClient.get()
                    .uri("/internal/api/v1/ai/models/{modelId}", expectedModelId)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException ex) {
            throw mapNetworkError(ex);
        } catch (RestClientResponseException ex) {
            throw mapErrorResponse(ex);
        } catch (RuntimeException ex) {
            throw mapRuntimeError(ex);
        }

        AiRuntimeModelInfo model = parseModelInfo(body);
        if (!expectedModelId.equals(model.modelId()) || !expectedMode.equals(model.mode())) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 模型身份与业务请求不一致");
        }
        if (!model.ready()) {
            throw new AiFastApiException("AI_MODEL_UNAVAILABLE", "FastAPI 模型尚未就绪");
        }
        if ("REAL".equals(expectedMode)) {
            if (!"APPROVED".equals(model.status())) {
                throw new AiFastApiException("AI_MODEL_UNAVAILABLE", "FastAPI 真实模型未通过准入");
            }
            if (!CUDA_EXECUTION_PROVIDER.equals(model.executionProvider())) {
                throw new AiFastApiException("AI_MODEL_UNAVAILABLE", "FastAPI 真实模型未运行在 CUDA");
            }
        }
        return model;
    }

    private AiImageQualityResponse parseImageQuality(String body, String requestId) {
        if (body == null || body.isBlank()) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回空图片质量响应");
        }
        try {
            AiImageQualityResponse response = objectMapper.readValue(body, AiImageQualityResponse.class);
            if (response == null
                    || response.requestId() == null
                    || response.requestId().isBlank()
                    || response.modelId() == null
                    || response.modelId().isBlank()
                    || response.modelVersion() == null
                    || response.modelVersion().isBlank()
                    || response.status() == null
                    || response.status().isBlank()
                    || response.decodeStatus() == null
                    || response.decodeStatus().isBlank()
                    || response.contentType() == null
                    || response.contentType().isBlank()
                    || response.width() <= 0
                    || response.height() <= 0) {
                throw new AiFastApiException(
                        "AI_SERVICE_INVALID_RESPONSE", "FastAPI 图片质量响应缺少必要字段");
            }
            if (!requestId.equals(response.requestId())) {
                throw new AiFastApiException(
                        "AI_SERVICE_INVALID_RESPONSE", "FastAPI 图片质量请求编号不一致");
            }
            return response;
        } catch (AiFastApiException ex) {
            throw ex;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AiFastApiException(
                    "AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回非法图片质量响应", ex);
        }
    }

    private AiRuntimeModelInfo parseModelInfo(String body) {
        if (body == null || body.isBlank()) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回空模型信息");
        }
        try {
            AiRuntimeModelInfo response = objectMapper.readValue(body, AiRuntimeModelInfo.class);
            if (response == null
                    || response.modelId() == null
                    || response.version() == null
                    || response.mode() == null
                    || response.status() == null) {
                throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 模型信息缺少必要字段");
            }
            return response;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回非法模型信息", ex);
        }
    }

    private AiInferenceResponse parseSuccess(
            String body, String requestId, Map<String, Object> metadata) {
        if (body == null || body.isBlank()) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回空响应");
        }
        AiInferenceResponse response;
        try {
            response = objectMapper.readValue(body, AiInferenceResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回非 JSON 响应", ex);
        }
        if (response == null
                || response.requestId() == null
                || response.status() == null
                || response.mode() == null
                || response.model() == null
                || response.image() == null) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 响应缺少必要字段");
        }
        if (!requestId.equals(response.requestId())) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 请求编号不一致");
        }
        String expectedMode = stringValue(metadata.get("mode"));
        String expectedModelId = stringValue(metadata.get("requestedModelId"));
        if (expectedMode != null && !expectedMode.equals(response.mode())) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 推理模式与请求不一致");
        }
        if (expectedModelId != null && !expectedModelId.equals(response.model().modelId())) {
            throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 推理模型与请求不一致");
        }
        validateDetections(response.detections());
        return response;
    }

    private void validateDetections(List<AiInferenceResponse.Detection> detections) {
        if (detections == null) {
            return;
        }
        for (AiInferenceResponse.Detection detection : detections) {
            AiInferenceResponse.BoundingBox box = detection.boundingBox();
            if (box == null) {
                throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "检测对象缺少检测框");
            }
            if (!inRange(box.x(), 0.0, 1.0)
                    || !inRange(box.y(), 0.0, 1.0)
                    || !inRangeExclusive(box.width(), 0.0, 1.0)
                    || !inRangeExclusive(box.height(), 0.0, 1.0)
                    || box.x() + box.width() > 1.0 + EPSILON
                    || box.y() + box.height() > 1.0 + EPSILON
                    || !inRange(detection.confidence(), 0.0, 1.0)) {
                throw new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回非法检测框");
            }
        }
    }

    private boolean inRange(double value, double min, double max) {
        return value >= min - EPSILON && value <= max + EPSILON;
    }

    private boolean inRangeExclusive(double value, double min, double max) {
        return value > min - EPSILON && value <= max + EPSILON;
    }

    private AiFastApiException mapNetworkError(ResourceAccessException ex) {
        if (hasTimeoutSignal(ex)) {
            return new AiFastApiException("AI_SERVICE_TIMEOUT", "FastAPI 调用超时", ex);
        }
        if (hasCause(ex, ConnectException.class)) {
            return new AiFastApiException("AI_SERVICE_UNAVAILABLE", "FastAPI 不可用", ex);
        }
        return new AiFastApiException("AI_SERVICE_UNAVAILABLE", "FastAPI 网络异常", ex);
    }

    private AiFastApiException mapRuntimeError(RuntimeException ex) {
        if (hasTimeoutSignal(ex)) {
            return new AiFastApiException("AI_SERVICE_TIMEOUT", "FastAPI 调用超时", ex);
        }
        if (hasCause(ex, ConnectException.class)) {
            return new AiFastApiException("AI_SERVICE_UNAVAILABLE", "FastAPI 不可用", ex);
        }
        return new AiFastApiException("AI_SERVICE_INVALID_RESPONSE", "FastAPI 请求失败", ex);
    }

    private boolean hasTimeoutSignal(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof SocketTimeoutException
                    || cursor instanceof HttpTimeoutException
                    || cursor instanceof TimeoutException) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (type.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private AiFastApiException mapErrorResponse(RestClientResponseException ex) {
        String errorCode = null;
        String errorMessage = "FastAPI 返回错误状态码 " + ex.getStatusCode().value();
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null && !body.isBlank()) {
                JsonNode detail = objectMapper.readTree(body);
                if (detail != null && detail.isObject()) {
                    String parsedCode = detail.path("errorCode").asText(null);
                    String parsedMessage = detail.path("errorMessage").asText(null);
                    if (parsedCode != null && !parsedCode.isBlank()) {
                        errorCode = parsedCode;
                    }
                    if (parsedMessage != null && !parsedMessage.isBlank()) {
                        errorMessage = parsedMessage;
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // 响应体不是标准错误结构，按状态码兜底。
        }
        if (errorCode == null) {
            int status = ex.getStatusCode().value();
            errorCode = status >= 500 ? "AI_SERVICE_UNAVAILABLE" : "AI_SERVICE_INVALID_RESPONSE";
        }
        return new AiFastApiException(errorCode, errorMessage, ex);
    }

    private static String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    /** 图片字节的匿名资源，提供固定文件名供 Multipart 使用。 */
    private static final class ImageResource extends ByteArrayResource {
        ImageResource(byte[] bytes) {
            super(bytes);
        }

        @Override
        public String getFilename() {
            return "image";
        }
    }

    /** 供 Service 构造推理元数据的辅助方法。 */
    public Map<String, Object> buildMetadata(
            String requestId, String mode, String assetId, String filename,
            String contentType, String sha256, String requestedModelId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("mode", mode);
        metadata.put("assetId", assetId);
        metadata.put("filename", filename);
        metadata.put("contentType", contentType);
        metadata.put("sha256", sha256);
        metadata.put("requestedModelId", requestedModelId);
        return metadata;
    }

    AiInferenceProperties getProperties() {
        return properties;
    }
}
