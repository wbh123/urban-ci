package org.urbansafe.priority.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 人工智能推理服务配置。
 *
 * <p>Spring Boot 通过该配置连接 FastAPI 内部推理接口。FastAPI 保持无状态，
 * 不直接写业务数据库；本配置只用于 Spring Boot 调用 FastAPI。
 */
@ConfigurationProperties(prefix = "urban-safe.ai")
public class AiInferenceProperties {

    /** FastAPI 内部推理基础地址。 */
    private String baseUrl = "http://localhost:8001";

    /** 连接超时（毫秒）。 */
    private int connectTimeoutMs = 3000;

    /** 读取超时（毫秒），用于识别 FastAPI 超时并映射为 AI_SERVICE_TIMEOUT。 */
    private int readTimeoutMs = 15000;

    /** 默认 MOCK 模型编号，与 ai.model_registry 登记一致。 */
    private String defaultMockModelId = "AI-DEFECT-MOCK-001";

    /** 默认推理模式。 */
    private String defaultMode = "MOCK";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public String getDefaultMockModelId() {
        return defaultMockModelId;
    }

    public void setDefaultMockModelId(String defaultMockModelId) {
        this.defaultMockModelId = defaultMockModelId;
    }

    public String getDefaultMode() {
        return defaultMode;
    }

    public void setDefaultMode(String defaultMode) {
        this.defaultMode = defaultMode;
    }
}
