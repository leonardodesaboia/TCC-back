package com.allset.api.shared.storage.domain;

public record StoredObject(StorageBucket bucket, String key, String contentType, long sizeBytes) {}
