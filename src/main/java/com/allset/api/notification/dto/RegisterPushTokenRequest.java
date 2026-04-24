package com.allset.api.notification.dto;

import com.allset.api.notification.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterPushTokenRequest(
        @NotBlank(message = "expoToken e obrigatorio")
        String expoToken,

        @NotNull(message = "platform e obrigatorio")
        Platform platform
) {
}
