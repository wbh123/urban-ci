package org.urbansafe.priority.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringAiProviderPropertiesTest {

    @Test
    void shouldDefaultToCurrentDeepSeekTextModelButRemainUnconfigured() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();

        assertThat(properties.getProviderType()).isEqualTo("DEEPSEEK");
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(properties.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(properties.getChatModel()).isEqualTo("none");
        assertThat(properties.configured()).isFalse();
    }

    @Test
    void configuredRequiresSpringAiChatModelAsWellAsDeepSeekCredentials() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();
        properties.setApiKey("secret");
        properties.setChatModel("none");
        assertThat(properties.configured()).isFalse();

        properties.setChatModel("openai");
        assertThat(properties.configured()).isTrue();
    }
}
