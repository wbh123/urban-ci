package org.urbansafe.priority.report.controller;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.asset.config.StorageProperties;
import org.urbansafe.priority.common.exception.DependencyUnavailableException;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

@Service
public class ReportStorageService {

    private final StorageProperties properties;
    private final String reportBucket;
    private final Path localRoot;

    public ReportStorageService(StorageProperties properties) {
        this.properties = properties;
        String configuredBucket = System.getenv("URBAN_SAFE_MINIO_REPORTS_BUCKET");
        this.reportBucket = configuredBucket == null || configuredBucket.isBlank()
                ? "urban-safe-reports"
                : configuredBucket.trim();
        Path assetRoot = Path.of(properties.getLocalDirectory()).toAbsolutePath().normalize();
        this.localRoot = assetRoot.resolveSibling("local-reports").normalize();
    }

    public StoredReport save(
            UUID buildingId,
            UUID reportId,
            String reportCode,
            byte[] bytes,
            UUID uploadedBy) {
        String filename = reportCode + ".pdf";
        String objectKey = "building-risk/" + buildingId + "/"
                + OffsetDateTime.now(ZoneOffset.UTC).toLocalDate() + "/"
                + reportId + ".pdf";
        try {
            String provider;
            String etag = null;
            if (properties.minioReady()) {
                provider = "MINIO";
                MinioClient client = client();
                boolean exists = client.bucketExists(
                        BucketExistsArgs.builder().bucket(reportBucket).build());
                if (!exists) {
                    throw new IllegalStateException(
                            "MinIO 报告存储桶不存在：" + reportBucket);
                }
                etag = client.putObject(PutObjectArgs.builder()
                                .bucket(reportBucket)
                                .object(objectKey)
                                .contentType("application/pdf")
                                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                                .build())
                        .etag();
            } else if (properties.localReady()) {
                provider = "LOCAL";
                Path path = localPath(objectKey);
                Files.createDirectories(path.getParent());
                Files.write(path, bytes);
            } else {
                throw new IllegalStateException("报告对象存储配置不可用");
            }
            return new StoredReport(
                    UUID.randomUUID(),
                    reportBucket,
                    objectKey,
                    filename,
                    provider,
                    etag,
                    sha256(bytes),
                    bytes,
                    uploadedBy);
        } catch (IOException ex) {
            throw new DependencyUnavailableException(
                    "REPORT_STORAGE_UNAVAILABLE", "报告文件保存失败");
        } catch (Exception ex) {
            throw new DependencyUnavailableException(
                    "REPORT_STORAGE_UNAVAILABLE", "报告对象存储写入失败");
        }
    }

    public byte[] read(String bucket, String objectKey, String provider) {
        try {
            if ("MINIO".equalsIgnoreCase(provider) && properties.minioReady()) {
                try (InputStream in = client().getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {
                    return in.readAllBytes();
                }
            }
            return Files.readAllBytes(localPath(objectKey));
        } catch (Exception ex) {
            throw new ResourceNotFoundException(
                    "REPORT_FILE_NOT_FOUND", "报告文件不可用");
        }
    }

    private MinioClient client() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    private Path localPath(String objectKey) {
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot)) {
            throw new InvalidRequestException(
                    "REPORT_PATH_INVALID", "报告对象路径无效");
        }
        return path;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("报告摘要计算失败", ex);
        }
    }
}

record StoredReport(
        UUID assetId,
        String bucket,
        String objectKey,
        String filename,
        String provider,
        String etag,
        String sha256,
        byte[] bytes,
        UUID uploadedBy) {}
