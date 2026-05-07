package com.allset.api.geocoding.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Endereço normalizado retornado pelo provider de geocoding")
public record NormalizedAddress(

    @Schema(description = "Logradouro normalizado", example = "Avenida Dom Luís", nullable = true)
    String street,

    @Schema(description = "Número", example = "1233", nullable = true)
    String number,

    @Schema(description = "Bairro", example = "Aldeota", nullable = true)
    String district,

    @Schema(description = "Cidade", example = "Fortaleza", nullable = true)
    String city,

    @Schema(description = "Sigla do estado", example = "CE", nullable = true)
    String state,

    @Schema(description = "CEP normalizado", example = "60160-230", nullable = true)
    String zipCode

) {}
