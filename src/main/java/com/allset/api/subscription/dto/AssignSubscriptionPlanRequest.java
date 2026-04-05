package com.allset.api.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignSubscriptionPlanRequest(

        @Schema(description = "ID do plano que sera contratado ou aplicado", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Plano de assinatura e obrigatorio")
        UUID subscriptionPlanId
) {}
