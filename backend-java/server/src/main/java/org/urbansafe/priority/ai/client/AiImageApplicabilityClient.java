package org.urbansafe.priority.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * FastAPI 本地图片语义适用性接口客户端。
 *
 * <p>复用 {@code aiFastApiRestClient} 的连接与超时配置，只负责语义门禁稳定契约；
 * 不访问业务数据库、不访问 MinIO，也不执行在线模型调用。
 */
public class AiImageApplicabilityClient {

    private static final Set<String> DECISIONS =
            Set.of("APPLICABLE", "NOT_APPLICABLE", "UNCERTAIN");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public AiImageApplicabilityClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /** 调用 FastAPI CPU 语义适用性门禁。 */
    public AiImageApplicabilityResponse analyze(byte[] imageBytes, String requestId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ImageResource(imageBytes));
        parts.add("requestId", requestId);

        String body;
        try {
            body = restClient.post()
                    .uri("/internal/api/v1/ai/image-applicability")
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

        return parse(body, requestId);
    }

    private AiImageApplicabilityResponse parse(String body, String requestId) {
        if (body == null || body.isBlank()) {
            throw new AiFastApiException(
                    "AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回空图片语义适用性响应");
        }
        try {
            AiImageApplicabilityResponse response =
                    objectMapper.readValue(body, AiImageApplicabilityResponse.class);
            if (response == null
                    || response.requestId() == null
                    || response.requestId().isBlank()
                    || response.modelId() == null
                    || response.modelId().isBlank()
                    || response.modelVersion() == null
                    || response.modelVersion().isBlank()
                    || response.status() == null
                    || response.status().isBlank()
                    || response.decision() == null
                    || !DECISIONS.contains(response.decision())
                    || response.reason() == null
                    || response.reason().isBlank()
                    || response.confidence() == null
                    || response.confidence() < 0.0
                    || response.confidence() > 1.0
                    || response.allowDify() == null) {
                throw new AiFastApiException(
                        "AI_SERVICE_INVALID_RESPONSE", "FastAPI 图片语义适用性响应缺少必要字段");
            }
            if (!requestId.equals(response.requestId())) {
                throw new AiFastApiException(
                        "AI_SERVICE_INVALID_RESPONSE", "FastAPI 图片语义适用性请求编号不一致");
            }
            if ("NOT_APPLICABLE".equals(response.decision())
                    && Boolean.TRUE.equals(response.allowDify())) {
                throw new AiFastApiException(
                        "AI_SERVICE_INVALID_RESPONSE", "NOT_APPLICABLE 响应不得继续调用 Dify");
            }
            if (!"NOT_APPLICABLE".equals(response.decision())
                    && !Boolean.TRUE.equals(response.allowDify())) {
                throw new AiFastApiException(
                        "AI_SERVICE_INVALID_RESPONSE", "APPLICABLE/UNCERTAIN 响应必须继续调用 Dify");
            }
            return response;
        } catch (AiFastApiException ex) {
            throw ex;
        } catch (JsonProcessingException ex) {
            throw new AiFastApiException(
                    "AI_SERVICE_INVALID_RESPONSE", "FastAPI 返回非法图片语义适用性响应", ex);
        }
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
        } catch (JsonProcessingException ignored) {
            // 响应体不是稳定错误结构时按状态码兜底。
        }
        if (errorCode == null) {
            int status = ex.getStatusCode().value();
            errorCode = status >= 500 ? "AI_SERVICE_UNAVAILABLE" : "AI_SERVICE_INVALID_RESPONSE";
        }
        return new AiFastApiException(errorCode, errorMessage, ex);
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

    /** 图片字节匿名 Multipart 资源。 */
    private static final class ImageResource extends ByteArrayResource {
        ImageResource(byte[] bytes) {
            super(bytes);
        }

        @Override
        public String getFilename() {
            return "image";
        }
    }
}
