package com.allset.api.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfessionalSubscriptionResponse(

        @Schema(description = "ID do perfil profissional") UUID professionalId,
        @Schema(description = "ID do plano ativo") UUID subscriptionPlanId,
        @Schema(description = "Nome do plano") String planName,
        @Schema(description = "Valor mensal do plano") BigDecimal priceMonthly,
        @Schema(description = "Destaca nas buscas") boolean highlightInSearch,
        @Schema(description = "Tem prioridade no Express") boolean expressPriority,
        @Schema(description = "Selo exibido no perfil") String badgeLabel,
        @Schema(description = "Data limite dos beneficios atuais") Instant subscriptionExpiresAt,
        @Schema(description = "Indica se a renovacao automatica continua ativa") boolean autoRenew,
        @Schema(description = "Data em que o cancelamento da renovacao foi solicitado") Instant subscriptionCancelledAt
) {}
