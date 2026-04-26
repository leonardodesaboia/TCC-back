package com.allset.api.shared.storage.service;

import com.allset.api.shared.storage.domain.PresignedUpload;
import com.allset.api.shared.storage.domain.StorageBucket;
import com.allset.api.shared.storage.domain.StoredObject;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;

public interface StorageService {

    StoredObject upload(StorageBucket bucket, String keyPrefix, MultipartFile file);

    StoredObject upload(StorageBucket bucket, String keyPrefix, InputStream content,
                        long sizeBytes, String contentType, String originalFilename);

    String generateDownloadUrl(StorageBucket bucket, String key);

    String generateDownloadUrl(StorageBucket bucket, String key, Duration ttl);

    PresignedUpload generatePresignedUpload(StorageBucket bucket, String keyPrefix, String contentType);

    void delete(StorageBucket bucket, String key);

    boolean exists(StorageBucket bucket, String key);
}
