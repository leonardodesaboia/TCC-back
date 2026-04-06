package com.allset.api.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proposta de um profissional em um pedido Express.
 * Retornada ao cliente para que ele possa comparar e escolher.
 *
 * <p>Não expõe nome, localização ou dados pessoais do profissional —
 * o frontend deve enriquecer com GET /api/v1/professionals/{professionalId}
 * se precisar exibir o perfil completo.
 */
public record ExpressProposalResponse(
        UUID professionalId,
        BigDecimal proposedAmount,
        Instant respondedAt,
        short queuePosition
) {}
