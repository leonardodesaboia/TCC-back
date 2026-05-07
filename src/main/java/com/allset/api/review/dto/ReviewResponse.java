package com.allset.api.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dados de uma avaliacao")
public record ReviewResponse(

        @Schema(description = "ID da avaliacao") UUID id,
        @Schema(description = "ID do pedido avaliado") UUID orderId,
        @Schema(description = "ID do usuario que avaliou") UUID reviewerId,
        @Schema(description = "ID do usuario que recebeu a avaliacao") UUID revieweeId,
        @Schema(description = "Nota atribuida") short rating,
        @Schema(description = "Comentario visivel na avaliacao publicada", nullable = true) String comment,
        @Schema(description = "Data de envio") Instant submittedAt,
        @Schema(description = "Data de publicacao", nullable = true) Instant publishedAt
) {}
