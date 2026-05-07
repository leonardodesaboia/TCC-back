package com.allset.api.integration.storage.domain;

import java.time.Instant;

public record PresignedUpload(String url, String key, Instant expiresAt) {}
