package org.urbansafe.priority.ai.orchestration;

/** 第七阶段稳定人工智能错误码。 */
public final class AiErrorCodes {

    public static final String AI_PROVIDER_DISABLED = "AI_PROVIDER_DISABLED";
    public static final String AI_PROVIDER_NOT_FOUND = "AI_PROVIDER_NOT_FOUND";
    public static final String AI_PROVIDER_NOT_CONFIGURED = "AI_PROVIDER_NOT_CONFIGURED";
    public static final String AI_PROVIDER_UNAVAILABLE = "AI_PROVIDER_UNAVAILABLE";
    public static final String AI_PROVIDER_TIMEOUT = "AI_PROVIDER_TIMEOUT";
    public static final String AI_PROVIDER_AUTH_FAILED = "AI_PROVIDER_AUTH_FAILED";
    public static final String AI_WORKFLOW_FAILED = "AI_WORKFLOW_FAILED";
    public static final String AI_MODEL_UNAVAILABLE = "AI_MODEL_UNAVAILABLE";
    public static final String AI_INVALID_RESPONSE = "AI_INVALID_RESPONSE";
    public static final String AI_OUTPUT_VALIDATION_FAILED = "AI_OUTPUT_VALIDATION_FAILED";
    public static final String AI_UNSUPPORTED_CAPABILITY = "AI_UNSUPPORTED_CAPABILITY";

    private AiErrorCodes() {
    }
}
