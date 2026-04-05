package com.allset.api.offering.dto;

import com.allset.api.offering.domain.PricingType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProfessionalOfferingResponse(

        @Schema(description = "ID do serviço") UUID id,
        @Schema(description = "ID do profissional") UUID professionalId,
        @Schema(description = "ID da categoria") UUID categoryId,
        @Schema(description = "Título do serviço") String title,
        @Schema(description = "Descrição do serviço") String description,
        @Schema(description = "Tipo de precificação") PricingType pricingType,
        @Schema(description = "Valor do serviço") BigDecimal price,
        @Schema(description = "Duração estimada em minutos") Integer estimatedDurationMinutes,
        @Schema(description = "Serviço ativo") boolean active,
        @Schema(description = "Data de criação") Instant createdAt
) {}
