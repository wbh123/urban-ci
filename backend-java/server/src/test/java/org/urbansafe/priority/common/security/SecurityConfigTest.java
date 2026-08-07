package org.urbansafe.priority.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.auth.config.AuthProperties;

class SecurityConfigTest {

    @Test
    void blankConfiguredSecretGeneratesEphemeralHmacKey() {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret("");
        SecurityConfig config = new SecurityConfig(null, null, properties, null);

        SecretKey key = config.jwtSecretKey();

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    void explicitSecretMustContainAtLeastThirtyTwoBytes() {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setSecret("too-short");
        SecurityConfig config = new SecurityConfig(null, null, properties, null);

        assertThatThrownBy(config::jwtSecretKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少需要 32 字节");
    }

    @Test
    void explicitValidSecretIsUsedWithoutModification() {
        AuthProperties properties = new AuthProperties();
        String configured = "0123456789abcdef0123456789abcdef";
        properties.getJwt().setSecret(configured);
        SecurityConfig config = new SecurityConfig(null, null, properties, null);

        SecretKey key = config.jwtSecretKey();

        assertThat(new String(key.getEncoded(), StandardCharsets.UTF_8)).isEqualTo(configured);
    }
}
