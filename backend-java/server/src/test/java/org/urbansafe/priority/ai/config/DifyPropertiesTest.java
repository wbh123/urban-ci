package org.urbansafe.priority.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DifyPropertiesTest {

    @Test
    void configuredShouldAcceptAnyConfiguredBusinessWorkflowWithoutImageAnalysis() {
        DifyProperties properties = new DifyProperties();
        properties.setBaseUrl("https://api.dify.ai/v1");
        DifyWorkflowProperties review = new DifyWorkflowProperties();
        review.setApiKey("review-secret");
        review.setAppId("review-app");
        review.setVersion("review-assist-v1.0.0");
        properties.setWorkflows(Map.of("review-assist", review));

        assertThat(properties.configured()).isTrue();
    }

    @Test
    void configuredShouldRemainFalseWhenNoWorkflowHasApiKey() {
        DifyProperties properties = new DifyProperties();
        properties.setBaseUrl("https://api.dify.ai/v1");
        DifyWorkflowProperties report = new DifyWorkflowProperties();
        report.setAppId("report-app");
        properties.setWorkflows(Map.of("report-draft", report));

        assertThat(properties.configured()).isFalse();
    }
}
