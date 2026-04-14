package com.allset.api.integration.asaas.dto;

import java.math.BigDecimal;

public record AsaasTransferRequest(
        String walletId,
        BigDecimal value
) {}
