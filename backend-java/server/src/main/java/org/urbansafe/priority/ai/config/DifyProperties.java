package org.urbansafe.priority.ai.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Dify Cloud 或自托管 Dify 的统一配置，支持多个应用使用独立 API Key。 */
@ConfigurationProperties(prefix = "urban-safe.ai.dify")
public class DifyProperties {

    private boolean enabled;
    private String baseUrl = "https://api.dify.ai/v1";
    /** 兼容字段：仅映射到 image-analysis。 */
    private String apiKey;
    /** 兼容字段：仅用于追溯，不参与 Dify Workflow API 路径。 */
    private String workflowId;
    /** 兼容字段：仅映射到 image-analysis。 */
    private String workflowVersion = "image-analysis-v1.0.1";
    private int connectTimeoutMs = 300000;
    private int readTimeoutMs = 300000;
    private Map<String, DifyWorkflowProperties> workflows = new LinkedHashMap<>();

    /**
     * Dify Provider 是否具备至少一个可调用的业务工作流。
     *
     * <p>视觉识别已经由 FastAPI 承担，因此不能再强制要求 image-analysis 才认定整个
     * Dify Provider 已配置。复核辅助、报告草稿或知识问答中任意一个工作流配置完整，
     * 都应允许治理状态把 Dify 识别为已配置。
     */
    public boolean configured() {
        if (!notBlank(baseUrl)) {
            return false;
        }
        for (DifyWorkflowProperties workflow : workflows.values()) {
            if (workflow != null && workflow.configured()) {
                return true;
            }
        }
        DifyWorkflowProperties legacyImage = resolveLegacyImageWorkflow();
        return legacyImage != null && legacyImage.configured();
    }

    public DifyWorkflowProperties resolveWorkflow(String configKey) {
        DifyWorkflowProperties explicit = workflows.get(configKey);
        if (explicit != null) {
            return explicit;
        }
        if (!"image-analysis".equals(configKey)) {
            return null;
        }
        return resolveLegacyImageWorkflow();
    }

    private DifyWorkflowProperties resolveLegacyImageWorkflow() {
        DifyWorkflowProperties legacy = new DifyWorkflowProperties();
        legacy.setApiKey(apiKey);
        legacy.setAppId(workflowId);
        legacy.setVersion(workflowVersion);
        return legacy;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public String getWorkflowVersion() { return workflowVersion; }
    public void setWorkflowVersion(String workflowVersion) { this.workflowVersion = workflowVersion; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public Map<String, DifyWorkflowProperties> getWorkflows() { return workflows; }
    public void setWorkflows(Map<String, DifyWorkflowProperties> workflows) {
        this.workflows = workflows == null ? new LinkedHashMap<>() : new LinkedHashMap<>(workflows);
    }
}
