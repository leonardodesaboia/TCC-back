package com.allset.api.professional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateGeoRequest(

        @Schema(description = "Disponível para pedidos Express")
        @NotNull(message = "geoActive é obrigatório")
        Boolean geoActive,

        @Schema(description = "Latitude", example = "-3.731862")
        @DecimalMin(value = "-90.0", message = "Latitude inválida")
        @DecimalMax(value = "90.0", message = "Latitude inválida")
        BigDecimal geoLat,

        @Schema(description = "Longitude", example = "-38.526669")
        @DecimalMin(value = "-180.0", message = "Longitude inválida")
        @DecimalMax(value = "180.0", message = "Longitude inválida")
        BigDecimal geoLng
) {}
