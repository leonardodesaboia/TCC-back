package com.allset.api.payment.dto;

import com.allset.api.payment.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentRequest(
        @NotNull(message = "Método de pagamento é obrigatório")
        PaymentMethod method
) {}
