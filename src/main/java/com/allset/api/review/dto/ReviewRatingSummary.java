package com.allset.api.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resumo de notas recebidas")
public record ReviewRatingSummary(

        @Schema(description = "Media das notas publicadas", nullable = true, example = "4.75")
        BigDecimal averageRating,

        @Schema(description = "Quantidade de avaliacoes publicadas", example = "12")
        long reviewCount
) {}
