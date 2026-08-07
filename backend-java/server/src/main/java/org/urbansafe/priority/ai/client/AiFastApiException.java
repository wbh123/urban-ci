package org.urbansafe.priority.ai.client;

import org.urbansafe.priority.ai.provider.AiProviderException;

/**
 * FastAPI 调用异常。
 *
 * <p>该异常属于 FastAPI 适配层，对业务层表现为通用 {@link AiProviderException}。
 * 错误码与 docs/10_开发阶段/03_第三阶段/02_人工智能调用链路与接口设计.md 第 8 节一致。
 */
public class AiFastApiException extends AiProviderException {

    /**
     * @param errorCode 稳定业务错误码，例如 AI_SERVICE_TIMEOUT
     * @param message   不含敏感信息的错误摘要
     */
    public AiFastApiException(String errorCode, String message) {
        super(errorCode, message);
    }

    public AiFastApiException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
