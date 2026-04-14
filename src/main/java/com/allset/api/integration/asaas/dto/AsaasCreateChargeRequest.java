package com.allset.api.integration.asaas.dto;

import java.math.BigDecimal;

public record AsaasCreateChargeRequest(
        String customer,
        String billingType,
        BigDecimal value,
        String description,
        String externalReference
) {}
