package com.allset.api.notification.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationPreferenceRequest(
        @NotNull(message = "notificationsEnabled e obrigatorio")
        Boolean notificationsEnabled
) {
}
