package org.urbansafe.priority.ai.client;

import java.net.SocketTimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClientResponseException;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.provider.AiProviderException;

/** 基于 Spring AI ChatClient 的受控多模态调用网关。 */
public class DefaultSpringAiChatGateway implements SpringAiChatGateway {

    private static final String OUTPUT_INSTRUCTION = """
            仅返回一个 JSON 对象，不要使用 Markdown。JSON 字段为：
            summary（必填字符串）、detections（数组）、riskSignals（数组）、
            recommendations（字符串数组）、confidence（零到一之间的数字或 null）、
            warnings（字符串数组）、modelVersion（可选字符串）。
            结果只用于辅助筛查，不要输出正式风险等级，不要修改业务数据。
            """;

    private final ChatClient chatClient;

    public DefaultSpringAiChatGateway(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String generate(AiOrchestrationRequest request) {
        try {
            return chatClient.prompt()
                    .user(user -> {
                        user.text(OUTPUT_INSTRUCTION + "\n任务：" + safePrompt(request.prompt()));
                        byte[] imageBytes = request.imageBytes();
                        if (imageBytes != null && imageBytes.length > 0) {
                            user.media(resolveMimeType(request.contentType()),
                                    new ByteArrayResource(imageBytes));
                        }
                    })
                    .call()
                    .content();
        } catch (RestClientResponseException ex) {
            throw mapHttpException(ex);
        } catch (Exception ex) {
            if (hasTimeoutCause(ex)) {
                throw new AiProviderException(
                        AiErrorCodes.AI_PROVIDER_TIMEOUT, "在线模型调用超时", ex);
            }
            throw new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_UNAVAILABLE, "在线模型暂时不可用", ex);
        }
    }

    private static String safePrompt(String prompt) {
        return prompt == null || prompt.isBlank() ? "分析输入并给出辅助建议" : prompt;
    }

    private static MimeType resolveMimeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        try {
            return MimeType.valueOf(contentType);
        } catch (IllegalArgumentException ex) {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }
    }

    private static AiProviderException mapHttpException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_AUTH_FAILED, "在线模型身份认证失败", ex);
        }
        if (status == 404) {
            return new AiProviderException(
                    AiErrorCodes.AI_MODEL_UNAVAILABLE, "在线模型不存在或不可用", ex);
        }
        if (status == 408 || status == 504) {
            return new AiProviderException(
                    AiErrorCodes.AI_PROVIDER_TIMEOUT, "在线模型调用超时", ex);
        }
        return new AiProviderException(
                AiErrorCodes.AI_PROVIDER_UNAVAILABLE, "在线模型暂时不可用", ex);
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
