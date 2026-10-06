package com.allset.api.order.dto;

import com.allset.api.shared.validation.NoHtml;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProposeServiceChangeRequest(

        @NotNull(message = "Novo serviço é obrigatório")
        UUID newServiceId,

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        @NoHtml
        String reason,

        @Min(value = 1, message = "Duração deve ser maior que zero")
        Integer estimatedDurationMinutes
) {}
