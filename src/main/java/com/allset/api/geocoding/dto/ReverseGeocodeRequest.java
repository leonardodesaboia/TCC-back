package com.allset.api.geocoding.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Ponto no mapa a ser convertido em endereço escrito")
public record ReverseGeocodeRequest(

    @Schema(description = "Latitude geográfica", example = "-3.731862")
    @NotNull(message = "Latitude é obrigatória")
    @DecimalMin(value = "-90.000000", message = "Latitude inválida")
    @DecimalMax(value = "90.000000", message = "Latitude inválida")
    BigDecimal lat,

    @Schema(description = "Longitude geográfica", example = "-38.526669")
    @NotNull(message = "Longitude é obrigatória")
    @DecimalMin(value = "-180.000000", message = "Longitude inválida")
    @DecimalMax(value = "180.000000", message = "Longitude inválida")
    BigDecimal lng

) {}
