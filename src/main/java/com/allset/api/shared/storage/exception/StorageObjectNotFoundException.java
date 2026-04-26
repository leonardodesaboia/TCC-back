package com.allset.api.shared.storage.exception;

import com.allset.api.shared.storage.domain.StorageBucket;

public class StorageObjectNotFoundException extends RuntimeException {

    public StorageObjectNotFoundException(StorageBucket bucket, String key) {
        super("Objeto não encontrado: bucket=%s key=%s".formatted(bucket.name(), key));
    }
}
