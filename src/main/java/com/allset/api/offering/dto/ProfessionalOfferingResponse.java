package com.allset.api.offering.dto;

import com.allset.api.offering.domain.PricingType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfessionalOfferingResponse(

        @Schema(description = "ID do servico") UUID id,
        @Schema(description = "ID do profissional") UUID professionalId,
        @Schema(description = "ID da categoria") UUID categoryId,
        @Schema(description = "Titulo do servico") String title,
        @Schema(description = "Descricao do servico") String description,
        @Schema(description = "Tipo de precificacao") PricingType pricingType,
        @Schema(description = "Valor definido no servico (null = usa valor/hora da especialidade)", nullable = true) BigDecimal price,
        @Schema(description = "Valor efetivo: price do servico ou hourly_rate da especialidade") BigDecimal effectivePrice,
        @Schema(description = "Duracao estimada em minutos") Integer estimatedDurationMinutes,
        @Schema(description = "Servico ativo") boolean active,
        @Schema(description = "Media das avaliacoes publicadas deste servico", nullable = true) BigDecimal averageRating,
        @Schema(description = "Quantidade de avaliacoes publicadas deste servico") long reviewCount,
        @Schema(description = "Data de criacao") Instant createdAt
) {}
