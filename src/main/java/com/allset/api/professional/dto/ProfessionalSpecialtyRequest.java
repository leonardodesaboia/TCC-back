package com.allset.api.professional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalSpecialtyRequest(

        @Schema(description = "ID da categoria profissional", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "Categoria profissional é obrigatória")
        UUID categoryId,

        @Schema(description = "Anos de experiência nesta categoria", example = "5")
        @NotNull(message = "Experiência por categoria é obrigatória")
        @Min(value = 0, message = "Anos de experiência não pode ser negativo")
        @Max(value = 99, message = "Anos de experiência deve ser no máximo 99")
        Short yearsOfExperience,

        @Schema(description = "Valor/hora opcional desta profissão", example = "80.00", nullable = true)
        @DecimalMin(value = "0.0", inclusive = false, message = "Valor/hora deve ser maior que zero")
        BigDecimal hourlyRate
) {}
