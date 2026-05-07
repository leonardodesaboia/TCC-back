package com.allset.api.order.dto;

import com.allset.api.shared.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateExpressOrderRequest(

        @NotNull(message = "Área de serviço é obrigatória")
        UUID areaId,

        @NotNull(message = "Categoria é obrigatória")
        UUID categoryId,

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 2000, message = "Descrição deve ter no máximo 2000 caracteres")
        @NoHtml
        String description,

        @NotNull(message = "Endereço é obrigatório")
        UUID addressId,

        @PositiveOrZero(message = "Taxa de urgência deve ser zero ou positiva")
        BigDecimal urgencyFee
) {}
