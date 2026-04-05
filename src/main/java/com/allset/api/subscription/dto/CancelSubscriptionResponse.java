package com.allset.api.subscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record CancelSubscriptionResponse(

        @Schema(description = "ID do perfil profissional") UUID professionalId,
        @Schema(description = "ID do plano que permanece ate o fim do ciclo") UUID subscriptionPlanId,
        @Schema(description = "Nome do plano") String planName,
        @Schema(description = "Data final dos beneficios atuais") Instant benefitsUntil,
        @Schema(description = "Mensagem de retorno") String message
) {}
