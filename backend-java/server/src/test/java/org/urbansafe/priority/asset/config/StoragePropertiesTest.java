package org.urbansafe.priority.asset.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StoragePropertiesTest {

    @Test
    void shouldAcceptCompleteMinioConfiguration() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("minio");
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("urban_safe_app");
        properties.setSecretKey("strong-secret-key");
        properties.setBucket("urban-safe-assets");

        properties.validateConfiguration();

        assertThat(properties.getProvider()).isEqualTo("MINIO");
        assertThat(properties.minioReady()).isTrue();
        assertThat(properties.localReady()).isFalse();
    }

    @Test
    void shouldRejectMinioWithoutApplicationCredentials() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("MINIO");
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("");
        properties.setSecretKey("");

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-key");
    }

    @Test
    void shouldAcceptExplicitLocalStorageForTests() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("LOCAL");
        properties.setLocalDirectory("target/test-assets");

        properties.validateConfiguration();

        assertThat(properties.localReady()).isTrue();
        assertThat(properties.minioReady()).isFalse();
    }

    @Test
    void shouldRejectUnknownProviderInsteadOfFallingBack() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("S3");

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅支持 MINIO 或 LOCAL");
    }

    @Test
    void shouldRejectInvalidPreviewExpiry() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("LOCAL");
        properties.setPreviewExpirySeconds(0);

        assertThatThrownBy(properties::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("preview-expiry-seconds");
    }
}
