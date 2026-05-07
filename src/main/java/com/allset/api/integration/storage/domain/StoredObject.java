package com.allset.api.integration.storage.domain;

public record StoredObject(StorageBucket bucket, String key, String contentType, long sizeBytes) {}
