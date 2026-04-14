package com.allset.api.integration.asaas.dto;

import java.math.BigDecimal;

public record AsaasRefundRequest(
        BigDecimal value,
        String description
) {}
