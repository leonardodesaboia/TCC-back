package com.allset.api.professional.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

import static com.allset.api.professional.domain.ProfessionalRules.MAX_SPECIALTIES;

public record UpdateProfessionalRequest(

        @Schema(description = "Apresentação do profissional")
        @NoHtml
        @Size(max = 1000, message = "Bio deve ter no maximo 1000 caracteres")
        String bio,

        @Schema(description = "Anos de experiência", example = "5")
        @Min(value = 0, message = "Anos de experiência não pode ser negativo")
        @Max(value = 99, message = "Anos de experiência deve ser no máximo 99")
        Short yearsOfExperience,

        @Schema(description = "Taxa horária base sugerida", example = "80.00")
        @DecimalMin(value = "0.0", inclusive = false, message = "Taxa horária deve ser maior que zero")
        BigDecimal baseHourlyRate,

        @Size(min = 1, message = "Selecione pelo menos uma profissão para atuar")
        @Size(max = MAX_SPECIALTIES, message = "Voce pode selecionar no maximo {max} profissoes")
        @Valid
        List<ProfessionalSpecialtyRequest> specialties
) {}
