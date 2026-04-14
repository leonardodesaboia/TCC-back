package com.allset.api.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminRefundRequest(
        @NotNull(message = "Valor do reembolso é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor do reembolso deve ser maior que zero")
        BigDecimal amount,

        @NotBlank(message = "Motivo do reembolso é obrigatório")
        String reason
) {}
