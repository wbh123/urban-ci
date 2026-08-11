package org.urbansafe.priority.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpringAiProviderPropertiesTest {

    @Test
    void shouldDefaultToCurrentDeepSeekTextModel() {
        SpringAiProviderProperties properties = new SpringAiProviderProperties();

        assertThat(properties.getProviderType()).isEqualTo("DEEPSEEK");
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(properties.getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(properties.configured()).isFalse();
    }
}
