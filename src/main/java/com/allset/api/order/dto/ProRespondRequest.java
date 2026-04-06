package com.allset.api.order.dto;

import com.allset.api.order.domain.ProResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProRespondRequest(

        @NotNull(message = "Resposta é obrigatória")
        ProResponse response,

        /**
         * Obrigatório quando response = accepted.
         * Validado no service — não pode ser feito via Bean Validation pois depende
         * do valor de outro campo.
         */
        @Positive(message = "Valor proposto deve ser positivo")
        BigDecimal proposedAmount
) {}
