package org.urbansafe.priority.ai.orchestration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 人工智能提供者默认路由配置。第一轮不允许自动降级。 */
@ConfigurationProperties(prefix = "urban-safe.ai.orchestration")
public class AiOrchestrationProperties {

    private String defaultVisionProvider = "FAST_API";
    private String defaultWorkflowProvider = "DIFY";
    private String defaultTextProvider = "SPRING_AI";

    public String defaultProvider(AiCapabilityType capabilityType) {
        if (capabilityType == null) {
            return null;
        }
        return switch (capabilityType) {
            case VISION_INFERENCE -> defaultVisionProvider;
            case WORKFLOW -> defaultWorkflowProvider;
            case TEXT_GENERATION -> defaultTextProvider;
        };
    }

    public String getDefaultVisionProvider() {
        return defaultVisionProvider;
    }

    public void setDefaultVisionProvider(String defaultVisionProvider) {
        this.defaultVisionProvider = defaultVisionProvider;
    }

    public String getDefaultWorkflowProvider() {
        return defaultWorkflowProvider;
    }

    public void setDefaultWorkflowProvider(String defaultWorkflowProvider) {
        this.defaultWorkflowProvider = defaultWorkflowProvider;
    }

    public String getDefaultTextProvider() {
        return defaultTextProvider;
    }

    public void setDefaultTextProvider(String defaultTextProvider) {
        this.defaultTextProvider = defaultTextProvider;
    }
}
