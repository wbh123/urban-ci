package org.urbansafe.priority.ai.provider;

/**
 * 人工智能能力提供者调用异常。
 *
 * <p>业务层只依赖稳定错误码和不暴露具体基础设施的错误摘要。底层实现可以是
 * FastAPI、本地模型、Dify 或在线模型供应商。
 */
public class AiProviderException extends RuntimeException {

    private final String errorCode;

    public AiProviderException(String errorCode, String message) {
        super(normalizeMessage(message));
        this.errorCode = errorCode;
    }

    public AiProviderException(String errorCode, String message, Throwable cause) {
        super(normalizeMessage(message), cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "模型服务调用失败";
        }
        return message
                .replace("FastAPI ", "模型服务")
                .replace("FastAPI", "模型服务");
    }
}
