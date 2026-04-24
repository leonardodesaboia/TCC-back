package com.allset.api.notification.dto;

import java.util.UUID;

public record NotificationPreferenceResponse(
        UUID userId,
        boolean notificationsEnabled
) {
}
