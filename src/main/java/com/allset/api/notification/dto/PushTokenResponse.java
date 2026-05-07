package com.allset.api.notification.dto;

import com.allset.api.notification.domain.Platform;

import java.time.Instant;
import java.util.UUID;

public record PushTokenResponse(
        UUID id,
        String expoToken,
        Platform platform,
        Instant createdAt,
        Instant lastSeen
) {
}
