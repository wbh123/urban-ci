package org.urbansafe.priority.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring Boot 调用 FastAPI 的推理服务配置。 */
@ConfigurationProperties(prefix = "urban-safe.ai")
public class AiInferenceProperties {

    private String baseUrl = "http://localhost:8001";
    private int connectTimeoutMs = 3000;
    /** FAST/PRECISION 与轻量接口读取超时。 */
    private int readTimeoutMs = 15000;
    /** ACCURACY 多模型档位独立读取超时，避免拖长其他 FastAPI 调用失败时间。 */
    private int accuracyReadTimeoutMs = 180000;
    private String defaultMockModelId = "AI-DEFECT-MOCK-001";
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

    public int getAccuracyReadTimeoutMs() {
        return accuracyReadTimeoutMs;
    }

    public void setAccuracyReadTimeoutMs(int accuracyReadTimeoutMs) {
        this.accuracyReadTimeoutMs = accuracyReadTimeoutMs;
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
