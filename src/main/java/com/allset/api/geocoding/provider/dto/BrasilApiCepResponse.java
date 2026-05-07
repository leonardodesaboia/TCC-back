package com.allset.api.geocoding.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mapeamento do payload do endpoint
 * {@code GET https://brasilapi.com.br/api/cep/v2/{cep}}.
 *
 * <p>O campo {@code service} indica qual fonte interna respondeu
 * (viacep, open-cep, postmon, widenet) — algumas devolvem {@code location}
 * vazio. Quando vier vazio, o provider trata como "endereço sem coords".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BrasilApiCepResponse(
    String cep,
    String state,
    String city,
    String neighborhood,
    String street,
    String service,
    Location location
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
        String type,
        Coordinates coordinates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coordinates(
        String latitude,
        String longitude
    ) {}
}
