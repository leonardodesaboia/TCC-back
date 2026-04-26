package com.allset.api.shared.storage.event;

import com.allset.api.shared.storage.domain.StorageBucket;

public record ObjectDeletionRequestedEvent(StorageBucket bucket, String key) {}
