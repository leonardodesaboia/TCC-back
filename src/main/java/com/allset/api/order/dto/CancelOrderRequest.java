package com.allset.api.order.dto;

import com.allset.api.shared.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @NotBlank(message = "Motivo do cancelamento é obrigatório")
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        @NoHtml
        String reason
) {}
