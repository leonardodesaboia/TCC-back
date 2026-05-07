package com.allset.api.dispute.dto;

import com.allset.api.dispute.domain.DisputeResolution;
import com.allset.api.dispute.domain.DisputeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Representacao de uma disputa")
public record DisputeResponse(

        @Schema(description = "ID da disputa")
        UUID id,

        @Schema(description = "ID do pedido associado")
        UUID orderId,

        @Schema(description = "ID do usuario que abriu a disputa (sempre o cliente)")
        UUID openedBy,

        @Schema(description = "Motivo informado na abertura")
        String reason,

        @Schema(description = "Status atual da disputa")
        DisputeStatus status,

        @Schema(description = "Tipo da resolucao — null enquanto nao resolvida", nullable = true)
        DisputeResolution resolution,

        @Schema(description = "Valor devolvido ao cliente apos resolucao", nullable = true)
        BigDecimal clientRefundAmount,

        @Schema(description = "Valor liberado ao profissional apos resolucao", nullable = true)
        BigDecimal professionalAmount,

        @Schema(description = "ID do admin que resolveu", nullable = true)
        UUID resolvedBy,

        @Schema(description = "Data/hora da resolucao", nullable = true)
        Instant resolvedAt,

        @Schema(description = "Data/hora de abertura")
        Instant openedAt,

        @Schema(description = "Notas internas do admin — visivel apenas para administradores", nullable = true)
        String adminNotes
) {}
