package com.allset.api.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateSubscriptionPlanRequest(

        @Schema(description = "Novo nome do plano", example = "Plano Premium")
        @Size(max = 60, message = "Nome deve ter no maximo 60 caracteres")
        String name,

        @Schema(description = "Novo valor mensal", example = "59.90")
        @DecimalMin(value = "0.00", inclusive = false, message = "Preco mensal deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "Preco mensal deve ter no maximo 8 inteiros e 2 casas decimais")
        BigDecimal priceMonthly,

        @Schema(description = "Destaca o profissional nas buscas", example = "false")
        Boolean highlightInSearch,

        @Schema(description = "Da prioridade no Express", example = "true")
        Boolean expressPriority,

        @Schema(description = "Novo selo exibido no perfil", example = "Premium")
        @Size(max = 30, message = "Selo deve ter no maximo 30 caracteres")
        String badgeLabel,

        @Schema(description = "Plano ativo", example = "true")
        Boolean active
) {}
