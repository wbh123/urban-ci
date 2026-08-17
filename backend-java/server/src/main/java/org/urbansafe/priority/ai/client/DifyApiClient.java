package org.urbansafe.priority.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.urbansafe.priority.ai.config.DifyProperties;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.workflow.AiWorkflowDefinition;
import org.urbansafe.priority.ai.workflow.AiWorkflowRegistry;

/** Dify 文件上传与 Workflow 阻塞调用客户端。 */
public class DifyApiClient implements DifyWorkflowClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DifyProperties properties;
    private final AiWorkflowRegistry workflowRegistry;

    public DifyApiClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            DifyProperties properties,
            AiWorkflowRegistry workflowRegistry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.workflowRegistry = workflowRegistry;
    }

    @Override
    public JsonNode run(AiOrchestrationRequest request) {
        AiWorkflowDefinition workflow = workflowRegistry.requireByWorkflowCode(request.modelCode());
        requireReady(workflow);
        try {
            String uploadFileId = uploadIfNecessary(request, workflow);
            // Dify Start 节点只接受工作流显式声明的输入变量。
            // workflowCode/workflowVersion/inputSchemaVersion 属于 Spring Boot 治理元数据，
            // 保留在本地注册表与审计中，不再无条件注入远端 Workflow inputs。
            Map<String, Object> inputs = new LinkedHashMap<>(request.inputs());
            if (uploadFileId != null) {
                inputs.put("image", Map.of(
                        "type", "image",
                        "transfer_method", "local_file",
                        "upload_file_id", uploadFileId));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("inputs", inputs);
            body.put("response_mode", "blocking");
            body.put("user", request.requestId());
            String responseBody = restClient.post()
                    .uri("/workflows/run")
                    .header("Authorization", bearer(workflow))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (responseBody == null || responseBody.isBlank()) {
                throw new AiProviderException(
                        AiErrorCodes.AI_INVALID_RESPONSE, "Dify 返回空响应");
            }
            return objectMapper.readTree(responseBody);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (ResourceAccessException ex) {
            if (hasTimeoutCause(ex)) {
                throw new AiProviderException(
                        AiErrorCodes.AI_PROVIDER_TIMEOUT, "Dify 工作流调用超时", ex);
            }
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_UNAVAILABLE, "Dify 服务暂时不可用", ex);
        } catch (Exception ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 响应无法解析", ex);
        }
    }

    private String uploadIfNecessary(
            AiOrchestrationRequest request,
            AiWorkflowDefinition workflow) {
        byte[] bytes = request.imageBytes();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "inspection-image" + extensionForContentType(request.contentType());
            }
        }, imagePartHeaders(request.contentType())));
        parts.add("user", request.requestId());
        String responseBody = restClient.post()
                .uri("/files/upload")
                .header("Authorization", bearer(workflow))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String fileId = root.path("id").asText(null);
            if (fileId == null || fileId.isBlank()) {
                throw new AiProviderException(
                        AiErrorCodes.AI_INVALID_RESPONSE, "Dify 文件上传响应缺少文件编号");
            }
            return fileId;
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(
                    AiErrorCodes.AI_INVALID_RESPONSE, "Dify 文件上传响应无法解析", ex);
        }
    }

    private void requireReady(AiWorkflowDefinition workflow) {
        if (!properties.isEnabled() || !workflow.enabled()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_DISABLED, "Dify 工作流未启用");
        }
        if (!workflow.configured()) {
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_NOT_CONFIGURED, "Dify 工作流尚未配置独立 API Key");
        }
    }

    private static String bearer(AiWorkflowDefinition workflow) {
        return "Bearer " + workflow.apiKey();
    }

    private static HttpHeaders imagePartHeaders(String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaTypeForContentType(contentType));
        return headers;
    }

    private static MediaType mediaTypeForContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.IMAGE_JPEG;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return mediaType.getType().equalsIgnoreCase("image") ? mediaType : MediaType.IMAGE_JPEG;
        } catch (IllegalArgumentException ex) {
            return MediaType.IMAGE_JPEG;
        }
    }

    private static String extensionForContentType(String contentType) {
        MediaType mediaType = mediaTypeForContentType(contentType);
        String subtype = mediaType.getSubtype().toLowerCase();
        return switch (subtype) {
            case "jpeg", "pjpeg" -> ".jpg";
            case "png" -> ".png";
            case "webp" -> ".webp";
            case "bmp" -> ".bmp";
            case "tiff", "tif" -> ".tif";
            default -> ".jpg";
        };
    }

    private static AiProviderException mapHttpException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_AUTH_FAILED, "Dify 身份认证失败", ex);
        }
        if (status == 408 || status == 504) {
            return new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_TIMEOUT, "Dify 工作流调用超时", ex);
        }
        if (status == 404) {
            return new AiProviderException(
                    AiErrorCodes.AI_WORKFLOW_FAILED, "Dify 工作流不存在或不可访问", ex);
        }
        if (status == 429) {
            return new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_UNAVAILABLE, "Dify 调用频率受限", ex);
        }
        if (status >= 500) {
            return new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_UNAVAILABLE, "Dify 服务暂时不可用", ex);
        }
        return new AiProviderException(
                AiErrorCodes.AI_WORKFLOW_FAILED, "Dify 工作流调用失败", ex);
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
