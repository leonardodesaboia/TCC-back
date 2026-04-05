package com.allset.api.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubscriptionPlanResponse(

        @Schema(description = "ID do plano") UUID id,
        @Schema(description = "Nome do plano") String name,
        @Schema(description = "Valor mensal do plano") BigDecimal priceMonthly,
        @Schema(description = "Destaca em buscas") boolean highlightInSearch,
        @Schema(description = "Tem prioridade no Express") boolean expressPriority,
        @Schema(description = "Selo exibido no perfil") String badgeLabel,
        @Schema(description = "Plano ativo") boolean active,
        @Schema(description = "Data de criacao") Instant createdAt
) {}
