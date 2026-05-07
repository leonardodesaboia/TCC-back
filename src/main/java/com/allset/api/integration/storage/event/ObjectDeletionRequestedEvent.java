package com.allset.api.integration.storage.event;

import com.allset.api.integration.storage.domain.StorageBucket;

public record ObjectDeletionRequestedEvent(StorageBucket bucket, String key) {}
