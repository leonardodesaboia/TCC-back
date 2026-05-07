package com.allset.api.professional.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalSpecialtyResponse(

        @Schema(description = "ID da categoria profissional") UUID categoryId,
        @Schema(description = "Nome da categoria profissional") String categoryName,
        @Schema(description = "ID da área da categoria") UUID areaId,
        @Schema(description = "Nome da área da categoria") String areaName,
        @Schema(description = "Anos de experiência nesta categoria") short yearsOfExperience,
        @Schema(description = "Valor/hora da profissão", nullable = true) BigDecimal hourlyRate
) {}
