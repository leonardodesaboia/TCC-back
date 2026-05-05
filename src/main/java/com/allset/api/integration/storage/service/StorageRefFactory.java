package com.allset.api.integration.storage.service;

import com.allset.api.integration.storage.config.MinioProperties;
import com.allset.api.integration.storage.domain.StorageBucket;
import com.allset.api.integration.storage.dto.StorageRefResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class StorageRefFactory {

    private final StorageService storageService;
    private final MinioProperties properties;

    public StorageRefResponse from(StorageBucket bucket, String key) {
        if (key == null || key.isBlank()) return null;
        String url = storageService.generateDownloadUrl(bucket, key);
        Instant expiresAt = bucket.visibility() == StorageBucket.Visibility.PUBLIC
                ? null
                : Instant.now().plus(Duration.ofMinutes(properties.presignedUrlTtlMinutes()));
        return new StorageRefResponse(key, url, expiresAt);
    }
}
