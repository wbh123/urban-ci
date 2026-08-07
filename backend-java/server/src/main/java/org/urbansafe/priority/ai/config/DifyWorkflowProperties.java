package org.urbansafe.priority.ai.config;

/** 单个 Dify 应用的隔离配置；API Key 仅来自环境变量。 */
public class DifyWorkflowProperties {
    private String apiKey;
    private String appId;
    private String version;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
