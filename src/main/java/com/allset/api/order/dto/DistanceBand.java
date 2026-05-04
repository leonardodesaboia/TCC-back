package com.allset.api.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Faixa de distância (em metros) entre o cliente e o profissional, " +
        "calculada como um terço do raio configurado. Não expõe a distância exata.")
public record DistanceBand(
        @Schema(description = "Rótulo legível da faixa", example = "100-200m")
        String label,

        @Schema(description = "Distância mínima da faixa, em metros (inclusive)", example = "100")
        int minMeters,

        @Schema(description = "Distância máxima da faixa, em metros (exclusive na 1ª/2ª faixa, inclusive na 3ª)",
                example = "200")
        int maxMeters
) {}
