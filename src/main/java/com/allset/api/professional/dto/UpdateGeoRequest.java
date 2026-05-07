package com.allset.api.professional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

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
        BigDecimal geoLng,

        @Schema(description = "Acurácia da medição em metros", example = "12.5")
        @DecimalMin(value = "0.0", message = "Acurácia inválida")
        @DecimalMax(value = "99999.99", message = "Acurácia inválida")
        BigDecimal accuracyMeters,

        @Schema(description = "Timestamp da captura no dispositivo (UTC)")
        Instant capturedAt,

        @Schema(description = "Origem da localização", example = "device-gps")
        @Size(max = 20, message = "source deve ter no máximo 20 caracteres")
        String source
) {}
