package com.allset.api.offering.dto;

import com.allset.api.offering.domain.PricingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProfessionalOfferingRequest(

        @Schema(description = "ID da categoria do serviço", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "Categoria é obrigatória")
        UUID categoryId,

        @Schema(description = "Título do serviço", example = "Instalação de tomadas")
        @NotBlank(message = "Título é obrigatório")
        @Size(max = 100, message = "Título deve ter no máximo 100 caracteres")
        String title,

        @Schema(description = "Descrição detalhada do serviço")
        String description,

        @Schema(description = "Tipo de precificação: hourly (por hora) ou fixed (preço fechado)")
        @NotNull(message = "Tipo de precificação é obrigatório")
        PricingType pricingType,

        @Schema(description = "Valor — por hora se hourly | total se fixed", example = "120.00")
        @NotNull(message = "Preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = false, message = "Preço deve ser maior que zero")
        BigDecimal price,

        @Schema(description = "Duração estimada em minutos — recomendado para fixed", example = "60")
        @Min(value = 1, message = "Duração deve ser maior que zero")
        Integer estimatedDurationMinutes
) {}
