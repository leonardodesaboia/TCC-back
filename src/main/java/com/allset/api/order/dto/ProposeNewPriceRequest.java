package com.allset.api.order.dto;

import com.allset.api.shared.validation.NoHtml;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProposeNewPriceRequest(

        @NotNull(message = "Novo valor é obrigatório")
        @Positive(message = "Novo valor deve ser positivo")
        @Digits(integer = 8, fraction = 2, message = "Formato monetario invalido")
        BigDecimal newAmount,

        @NotBlank(message = "Motivo é obrigatório")
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        @NoHtml
        String reason
) {}
