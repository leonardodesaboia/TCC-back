package com.allset.api.notification.dto;

import com.allset.api.notification.domain.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        JsonNode data,
        Instant sentAt,
        Instant readAt,
        Instant createdAt
) {
}
