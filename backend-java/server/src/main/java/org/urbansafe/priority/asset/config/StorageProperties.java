package org.urbansafe.priority.asset.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "urban-safe.storage")
public class StorageProperties {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("MINIO", "LOCAL");

    private String provider = "LOCAL";
    private String endpoint = "http://localhost:9000";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "urban-safe-assets";
    private String localDirectory = "data/local-assets";
    private long maxImageSizeBytes = 10L * 1024L * 1024L;
    private int previewExpirySeconds = 900;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getLocalDirectory() {
        return localDirectory;
    }

    public void setLocalDirectory(String localDirectory) {
        this.localDirectory = localDirectory;
    }

    public long getMaxImageSizeBytes() {
        return maxImageSizeBytes;
    }

    public void setMaxImageSizeBytes(long maxImageSizeBytes) {
        this.maxImageSizeBytes = maxImageSizeBytes;
    }

    public int getPreviewExpirySeconds() {
        return previewExpirySeconds;
    }

    public void setPreviewExpirySeconds(int previewExpirySeconds) {
        this.previewExpirySeconds = previewExpirySeconds;
    }

    @PostConstruct
    public void validateConfiguration() {
        provider = normalizeProvider(provider);
        if (!SUPPORTED_PROVIDERS.contains(provider)) {
            throw new IllegalStateException(
                    "urban-safe.storage.provider 仅支持 MINIO 或 LOCAL，当前值：" + provider);
        }

        requireText(bucket, "urban-safe.storage.bucket 不能为空");
        if (maxImageSizeBytes <= 0) {
            throw new IllegalStateException("urban-safe.storage.max-image-size-bytes 必须大于 0");
        }
        if (previewExpirySeconds <= 0 || previewExpirySeconds > 604800) {
            throw new IllegalStateException(
                    "urban-safe.storage.preview-expiry-seconds 必须在 1 至 604800 秒之间");
        }

        if ("MINIO".equals(provider)) {
            requireText(endpoint, "使用 MINIO 时 urban-safe.storage.endpoint 不能为空");
            requireText(accessKey, "使用 MINIO 时 urban-safe.storage.access-key 不能为空");
            requireText(secretKey, "使用 MINIO 时 urban-safe.storage.secret-key 不能为空");
        } else {
            requireText(localDirectory, "使用 LOCAL 时 urban-safe.storage.local-directory 不能为空");
        }
    }

    public boolean minioReady() {
        return "MINIO".equalsIgnoreCase(provider)
                && hasText(endpoint)
                && hasText(accessKey)
                && hasText(secretKey);
    }

    public boolean localReady() {
        return "LOCAL".equalsIgnoreCase(provider) && hasText(localDirectory);
    }

    private String normalizeProvider(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalStateException(message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
