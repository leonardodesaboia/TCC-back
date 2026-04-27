package com.allset.api.shared.storage.domain;

import java.time.Instant;

public record PresignedUpload(String url, String key, Instant expiresAt) {}
