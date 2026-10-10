package com.allset.api.address.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * De onde veio a coordenada de um endereço salvo.
 *
 * <p>O modo Express notifica profissionais num raio curto em volta desse ponto,
 * então a origem importa tanto quanto o valor: uma coordenada sugerida por
 * provider de CEP pode ser o centroide do município e cair quilômetros fora.
 */
@Schema(description = "Origem da coordenada do endereço")
public enum CoordinateSource {

    /** Capturada pelo GPS do aparelho, no local. Mais confiável. */
    device_gps,

    /** Marcada ou ajustada pelo usuário no mapa. Confiável. */
    user_pin,

    /** Sugerida pelo provider de geocoding e aceita sem ajuste. Confiável só com precisão de edifício. */
    geocoded,

    /** Gravada antes da V25, quando a API geocodificava sozinha. Não confiável. */
    legacy;

    /** Origens em que a coordenada é confiável independentemente da confiança do provider. */
    public boolean isUserConfirmed() {
        return this == device_gps || this == user_pin;
    }
}
