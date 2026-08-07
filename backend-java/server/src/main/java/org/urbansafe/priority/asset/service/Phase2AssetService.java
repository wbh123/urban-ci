package org.urbansafe.priority.asset.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.urbansafe.priority.asset.config.StorageProperties;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

@Service
public class Phase2AssetService {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final StorageProperties properties;
    private final Phase2Repository repository;

    public Phase2AssetService(StorageProperties properties, Phase2Repository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Transactional
    public Map<String, Object> upload(
            MultipartFile file, String businessType, UUID businessId, String bindingRole) {
        validate(file, businessType, businessId);
        try {
            byte[] bytes = file.getBytes();
            UUID id = UUID.randomUUID();
            String filename = safe(file.getOriginalFilename());
            String key = businessType.toLowerCase() + "/" + businessId + "/"
                    + OffsetDateTime.now().toLocalDate() + "/" + id
                    + extension(filename, file.getContentType());

            String provider;
            String etag = null;
            if (properties.minioReady()) {
                provider = "MINIO";
                etag = putMinio(key, file.getContentType(), bytes);
            } else if (properties.localReady()) {
                provider = "LOCAL";
                putLocal(key, bytes);
            } else {
                throw new IllegalStateException("对象存储配置不可用");
            }

            Map<String, Object> saved = repository.createAsset(
                    id,
                    properties.getBucket(),
                    key,
                    filename,
                    file.getContentType(),
                    bytes.length,
                    sha256(bytes),
                    provider,
                    etag,
                    businessType.toUpperCase(),
                    businessId,
                    bindingRole == null || bindingRole.isBlank()
                            ? "INSPECTION_PHOTO"
                            : bindingRole.toUpperCase());
            saved.put("previewUrl", previewUrl(id));
            return saved;
        } catch (InvalidRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("图片保存失败", ex);
        }
    }

    public List<Map<String, Object>> list(String businessType, UUID businessId) {
        if (businessType == null || businessType.isBlank() || businessId == null) {
            throw new InvalidRequestException(
                    "ASSET_BINDING_REQUIRED", "businessType 和 businessId 不能为空");
        }
        List<Map<String, Object>> result =
                repository.listAssets(businessType.toUpperCase(), businessId);
        result.forEach(item -> item.put("previewUrl", previewUrl((UUID) item.get("assetId"))));
        return result;
    }

    public Map<String, Object> get(UUID id) {
        return repository.findAsset(id).orElseThrow(
                () -> new ResourceNotFoundException("ASSET_NOT_FOUND", "图片不存在"));
    }

    public String previewUrl(UUID id) {
        Map<String, Object> asset = get(id);
        if ("MINIO".equals(asset.get("storageProvider")) && properties.minioReady()) {
            try {
                return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(String.valueOf(asset.get("bucket")))
                        .object(String.valueOf(asset.get("objectKey")))
                        .expiry(properties.getPreviewExpirySeconds())
                        .build());
            } catch (Exception ex) {
                throw new IllegalStateException("无法生成图片预览地址", ex);
            }
        }
        return "/api/v1/assets/" + id + "/content";
    }

    public byte[] content(UUID id) {
        Map<String, Object> asset = get(id);
        try {
            if ("MINIO".equals(asset.get("storageProvider")) && properties.minioReady()) {
                try (InputStream in = client().getObject(GetObjectArgs.builder()
                        .bucket(String.valueOf(asset.get("bucket")))
                        .object(String.valueOf(asset.get("objectKey")))
                        .build())) {
                    return in.readAllBytes();
                }
            }
            return Files.readAllBytes(localPath(String.valueOf(asset.get("objectKey"))));
        } catch (Exception ex) {
            throw new ResourceNotFoundException(
                    "ASSET_CONTENT_NOT_FOUND", "图片内容不可用");
        }
    }

    private void validate(MultipartFile file, String businessType, UUID businessId) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("ASSET_FILE_REQUIRED", "请选择图片");
        }
        if (!TYPES.contains(file.getContentType())) {
            throw new InvalidRequestException(
                    "ASSET_TYPE_UNSUPPORTED", "仅支持 JPEG、PNG、WebP");
        }
        if (file.getSize() > properties.getMaxImageSizeBytes()) {
            throw new InvalidRequestException(
                    "ASSET_TOO_LARGE", "图片不能超过配置的大小限制");
        }
        if (businessType == null || businessType.isBlank() || businessId == null) {
            throw new InvalidRequestException(
                    "ASSET_BINDING_REQUIRED", "图片必须绑定业务对象");
        }
    }

    private String putMinio(String key, String type, byte[] bytes) throws Exception {
        MinioClient minioClient = client();
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucket())
                .build());
        if (!bucketExists) {
            throw new IllegalStateException(
                    "MinIO 存储桶不存在，请先运行 minio-init：" + properties.getBucket());
        }
        return minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(key)
                        .contentType(type)
                        .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                        .build())
                .etag();
    }

    private void putLocal(String key, byte[] bytes) throws Exception {
        Path path = localPath(key);
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private MinioClient client() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    private Path localPath(String key) {
        Path root = Path.of(properties.getLocalDirectory()).toAbsolutePath().normalize();
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new InvalidRequestException("ASSET_PATH_INVALID", "对象路径无效");
        }
        return path;
    }

    private String safe(String name) {
        String value = name == null || name.isBlank()
                ? "image"
                : Path.of(name).getFileName().toString();
        return value.replaceAll("[^\\p{L}\\p{N}._-]", "_");
    }

    private String extension(String name, String type) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            return name.substring(dot).toLowerCase();
        }
        return "image/png".equals(type) ? ".png" : "image/webp".equals(type) ? ".webp" : ".jpg";
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
