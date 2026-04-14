package com.allset.api.integration.asaas.dto;

import java.math.BigDecimal;

public record AsaasChargeResponse(
        String id,
        String status,
        String billingType,
        BigDecimal value,
        String pixQrCodeUrl,
        String pixCopyPaste,
        String invoiceUrl
) {}
