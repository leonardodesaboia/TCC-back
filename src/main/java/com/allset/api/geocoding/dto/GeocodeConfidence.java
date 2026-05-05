package com.allset.api.geocoding.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nível de confiança do resultado de geocoding")
public enum GeocodeConfidence {

    /** Coordenada precisa do edifício/casa. */
    ROOFTOP,

    /** Coordenada interpolada ao longo da via. */
    INTERPOLATED,

    /** Coordenada aproximada do bairro/cidade. */
    CITY,

    /** Endereço não localizável. */
    NOT_FOUND
}
