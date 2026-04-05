package com.allset.api.professional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProfessionalRequest(

        @Schema(description = "ID do usuário vinculado", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "ID do usuário é obrigatório")
        UUID userId,

        @Schema(description = "Apresentação do profissional")
        String bio,

        @Schema(description = "Anos de experiência", example = "5")
        @Min(value = 0, message = "Anos de experiência não pode ser negativo")
        @Max(value = 99, message = "Anos de experiência deve ser no máximo 99")
        Short yearsOfExperience,

        @Schema(description = "Taxa horária base sugerida", example = "80.00")
        @DecimalMin(value = "0.0", inclusive = false, message = "Taxa horária deve ser maior que zero")
        BigDecimal baseHourlyRate
) {}
