package com.allset.api.integration.asaas.dto;

import java.math.BigDecimal;

public record AsaasWebhookEvent(
        String event,
        PaymentPayload payment
) {
    public record PaymentPayload(
            String id,
            String status,
            BigDecimal value,
            String externalReference
    ) {}
}
