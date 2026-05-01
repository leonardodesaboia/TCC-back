package com.allset.api.notification.dto;

import com.allset.api.notification.domain.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterPushTokenRequest(
        @NotBlank(message = "expoToken e obrigatorio")
        @Size(max = 512, message = "expoToken deve ter no maximo 512 caracteres")
        String expoToken,

        @NotNull(message = "platform e obrigatorio")
        Platform platform
) {
}
