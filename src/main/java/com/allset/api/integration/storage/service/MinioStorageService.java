package com.allset.api.integration.storage.service;

import com.allset.api.integration.storage.config.MinioProperties;
import com.allset.api.integration.storage.domain.PresignedUpload;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.domain.StoredObject;
import com.allset.api.integration.storage.exception.FileTooLargeException;
import com.allset.api.integration.storage.exception.InvalidFileTypeException;
import com.allset.api.integration.storage.exception.StorageUploadException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class MinioStorageService implements StorageService {

    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "image/jpeg",     "jpg",
            "image/png",      "png",
            "image/svg+xml",  "svg",
            "application/pdf", "pdf",
            "application/zip", "zip"
    );

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final MinioProperties properties;

    public MinioStorageService(MinioClient minioClient,
                               @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                               MinioProperties properties) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.properties = properties;
    }

    @Override
    public StoredObject upload(StorageBucket bucket, String keyPrefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório e não pode estar vazio");
        }

        long maxBytes = bucket.maxSizeBytes(properties.maxSizeMb());
        if (file.getSize() > maxBytes) {
            throw new FileTooLargeException(file.getSize(), maxBytes);
        }

        try (InputStream stream = file.getInputStream()) {
            return uploadInternal(bucket, keyPrefix, stream, file.getSize(),
                    file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new StorageUploadException("Falha ao ler arquivo de upload", e);
        }
    }

    @Override
    public StoredObject upload(StorageBucket bucket, String keyPrefix, InputStream content,
                               long sizeBytes, String contentType, String originalFilename) {
        long maxBytes = bucket.maxSizeBytes(properties.maxSizeMb());
        if (sizeBytes > maxBytes) {
            throw new FileTooLargeException(sizeBytes, maxBytes);
        }
        return uploadInternal(bucket, keyPrefix, content, sizeBytes, contentType, originalFilename);
    }

    private StoredObject uploadInternal(StorageBucket bucket, String keyPrefix, InputStream content,
                                        long sizeBytes, String declaredMime, String originalFilename) {
        BufferedInputStream buffered = new BufferedInputStream(content);
        String effectiveMime = detectMimeType(buffered, declaredMime);

        if (!bucket.allowedMimeTypes().contains(effectiveMime)) {
            throw new InvalidFileTypeException(bucket, effectiveMime);
        }

        String extension = EXTENSION_BY_MIME.getOrDefault(effectiveMime, fallbackExtension(originalFilename));
        String sanitizedPrefix = sanitizePrefix(keyPrefix);
        String key = sanitizedPrefix + "/" + UUID.randomUUID() + "." + extension;
        String bucketName = bucket.fullName(properties.bucketPrefix());

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .stream(buffered, sizeBytes, -1)
                    .contentType(effectiveMime)
                    .build());

            log.info("event=storage_upload bucket={} key={} size={} mime={}",
                    bucketName, key, sizeBytes, effectiveMime);

            return new StoredObject(bucket, key, effectiveMime, sizeBytes);
        } catch (Exception e) {
            log.error("event=storage_upload_failed bucket={} key={} error={}",
                    bucketName, key, e.getMessage());
            throw new StorageUploadException("Falha ao enviar arquivo ao storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateDownloadUrl(StorageBucket bucket, String key) {
        return generateDownloadUrl(bucket, key, Duration.ofMinutes(properties.presignedUrlTtlMinutes()));
    }

    @Override
    public String generateDownloadUrl(StorageBucket bucket, String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String bucketName = bucket.fullName(properties.bucketPrefix());

        if (bucket.visibility() == StorageBucket.Visibility.PUBLIC) {
            return "%s/%s/%s".formatted(stripTrailingSlash(properties.publicEndpoint()), bucketName, key);
        }

        try {
            return publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .method(Method.GET)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageUploadException("Falha ao gerar URL pré-assinada: " + e.getMessage(), e);
        }
    }

    @Override
    public PresignedUpload generatePresignedUpload(StorageBucket bucket, String keyPrefix, String contentType) {
        if (!bucket.allowedMimeTypes().contains(contentType)) {
            throw new InvalidFileTypeException(bucket, contentType);
        }

        String extension = EXTENSION_BY_MIME.getOrDefault(contentType, "bin");
        String sanitizedPrefix = sanitizePrefix(keyPrefix);
        String key = sanitizedPrefix + "/" + UUID.randomUUID() + "." + extension;
        String bucketName = bucket.fullName(properties.bucketPrefix());

        Duration ttl = Duration.ofMinutes(properties.presignedUrlTtlMinutes());

        try {
            String url = publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .method(Method.PUT)
                    .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
                    .build());
            return new PresignedUpload(url, key, Instant.now().plus(ttl));
        } catch (Exception e) {
            throw new StorageUploadException("Falha ao gerar URL pré-assinada de upload: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(StorageBucket bucket, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        String bucketName = bucket.fullName(properties.bucketPrefix());
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .build());
            log.info("event=storage_delete bucket={} key={}", bucketName, key);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return;
            }
            throw new StorageUploadException("Falha ao remover objeto: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageUploadException("Falha ao remover objeto: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(StorageBucket bucket, String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String bucketName = bucket.fullName(properties.bucketPrefix());
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(key)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new StorageUploadException("Falha ao verificar objeto: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageUploadException("Falha ao verificar objeto: " + e.getMessage(), e);
        }
    }

    private String detectMimeType(BufferedInputStream stream, String declared) {
        try {
            stream.mark(64);
            String detected = URLConnection.guessContentTypeFromStream(stream);
            stream.reset();
            if (detected != null && !detected.isBlank()) {
                return detected;
            }
        } catch (IOException ignored) {
            // ignora — cai no fallback
        }
        if (declared != null && !declared.isBlank()) {
            return declared;
        }
        return "application/octet-stream";
    }

    private String sanitizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "misc";
        }
        String cleaned = prefix
                .replace("..", "")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .replaceAll("[^a-zA-Z0-9/_-]", "-");
        return cleaned.isBlank() ? "misc" : cleaned;
    }

    private String fallbackExtension(String originalFilename) {
        if (originalFilename == null) return "bin";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "bin";
        return originalFilename.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
