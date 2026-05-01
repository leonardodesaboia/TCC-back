package com.allset.api.offering.dto;

import com.allset.api.offering.domain.PricingType;
import com.allset.api.shared.validation.NoHtml;
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
        @NoHtml
        String title,

        @Schema(description = "Descrição detalhada do serviço")
        @NoHtml
        @Size(max = 1000, message = "Descricao deve ter no maximo 1000 caracteres")
        String description,

        @Schema(description = "Tipo de precificação: hourly (por hora) ou fixed (preço fechado)")
        @NotNull(message = "Tipo de precificação é obrigatório")
        PricingType pricingType,

        @Schema(description = "Valor — por hora se hourly | total se fixed. Quando omitido em hourly, usa o valor/hora da especialidade.", example = "120.00")
        @DecimalMin(value = "0.0", inclusive = false, message = "Preço deve ser maior que zero")
        BigDecimal price,

        @Schema(description = "Duração estimada em minutos — recomendado para fixed", example = "60")
        @Min(value = 1, message = "Duração deve ser maior que zero")
        Integer estimatedDurationMinutes
) {}
