package com.allset.api.geocoding.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Coordenadas e endereço normalizado retornados pelo provider de geocoding")
public record GeocodeResponse(

    @Schema(description = "Latitude geográfica", example = "-3.731862")
    BigDecimal lat,

    @Schema(description = "Longitude geográfica", example = "-38.526669")
    BigDecimal lng,

    @Schema(description = "Endereço completo formatado pelo provider",
            example = "Avenida Dom Luís, 1233, Aldeota, Fortaleza, CE, 60160-230, Brasil")
    String displayName,

    @Schema(description = "Endereço normalizado em campos estruturados")
    NormalizedAddress normalizedAddress,

    @Schema(description = "Nível de confiança do resultado", example = "ROOFTOP")
    GeocodeConfidence confidence,

    @Schema(description = "Identificador do provider que gerou o resultado", example = "nominatim")
    String provider

) {}
