package com.allset.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proposta de um profissional em um pedido Express.
 * Retornada ao cliente para que ele possa comparar e escolher.
 *
 * <p>Não expõe nome, localização exata ou dados pessoais do profissional —
 * o frontend deve enriquecer com GET /api/v1/professionals/{professionalId}
 * se precisar exibir o perfil completo.
 */
@Schema(description = "Proposta de um profissional em um pedido Express")
public record ExpressProposalResponse(
        @Schema(description = "ID do profissional", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID professionalId,

        @Schema(description = "Valor proposto pelo profissional", example = "120.00")
        BigDecimal proposedAmount,

        @Schema(description = "Quando o profissional enviou a proposta")
        Instant respondedAt,

        @Schema(description = "Posição do profissional na fila de notificação", example = "3")
        short queuePosition,

        @Schema(description = "Faixa de distância entre o endereço do cliente e a localização do profissional. " +
                "Não expõe a distância exata para preservar a privacidade do profissional.")
        DistanceBand distanceBand
) {}
