package com.allset.api.integration.asaas.dto;

import java.math.BigDecimal;

public record AsaasTransferResponse(
        String id,
        String status,
        BigDecimal value
) {}
