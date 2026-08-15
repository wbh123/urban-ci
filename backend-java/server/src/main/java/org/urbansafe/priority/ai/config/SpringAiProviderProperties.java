package org.urbansafe.priority.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring AI DeepSeek 文本模型直连配置。 */
@ConfigurationProperties(prefix = "urban-safe.ai.spring-ai-provider")
public class SpringAiProviderProperties {

    public static final String DEFAULT_PROVIDER_TYPE = "DEEPSEEK";
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";

    private boolean enabled;
    private String providerType = DEFAULT_PROVIDER_TYPE;
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private String model = DEFAULT_MODEL;
    /** Spring Boot 实际启用的 Spring AI Chat 模型类型；DeepSeek 走 OpenAI 兼容协议。 */
    private String chatModel = "none";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;

    public boolean configured() {
        return notBlank(providerType)
                && notBlank(apiKey)
                && notBlank(baseUrl)
                && notBlank(model)
                && chatModelEnabled();
    }

    public boolean chatModelEnabled() {
        return notBlank(chatModel) && !"none".equalsIgnoreCase(chatModel.trim());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String chatModel) { this.chatModel = chatModel; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
}
