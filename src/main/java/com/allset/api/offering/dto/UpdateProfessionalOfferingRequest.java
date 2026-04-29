package com.allset.api.offering.dto;

import com.allset.api.offering.domain.PricingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateProfessionalOfferingRequest(

        @Schema(description = "Título do serviço")
        @Size(max = 100, message = "Título deve ter no máximo 100 caracteres")
        String title,

        @Schema(description = "Descrição detalhada do serviço")
        String description,

        @Schema(description = "Tipo de precificação")
        PricingType pricingType,

        @Schema(description = "Valor do serviço")
        @DecimalMin(value = "0.0", inclusive = false, message = "Preço deve ser maior que zero")
        BigDecimal price,

        @Schema(description = "Duração estimada em minutos")
        @Min(value = 1, message = "Duração deve ser maior que zero")
        Integer estimatedDurationMinutes,

        @Schema(description = "Serviço ativo")
        Boolean active,

        @Schema(description = "Quando true, remove o preço próprio e passa a usar o valor/hora da especialidade (apenas para hourly)")
        Boolean clearPrice
) {}
