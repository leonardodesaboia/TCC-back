package com.allset.api.geocoding.provider;

import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.NormalizedAddress;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * SPI agnóstica do fornecedor de geocoding. O service de alto nível
 * orquestra cache + tratamento de erros em cima desta interface.
 */
public interface GeocodingProvider {

    /** Identificador curto do provider (ex: "nominatim"). Vai para o DTO de resposta. */
    String name();

    /**
     * Busca coordenadas para o endereço informado.
     *
     * @return resultado preenchido quando o endereço foi localizado;
     *         {@link Optional#empty()} quando o provider devolveu sem matches.
     */
    Optional<GeocodeResult> geocode(GeocodeQuery query);

    record GeocodeQuery(
        String zipCode,
        String street,
        String number,
        String district,
        String city,
        String state,
        String country
    ) {
        public static GeocodeQuery brazilianAddress(
            String zipCode, String street, String number,
            String district, String city, String state
        ) {
            return new GeocodeQuery(zipCode, street, number, district, city, state, "BR");
        }
    }

    record GeocodeResult(
        BigDecimal lat,
        BigDecimal lng,
        String displayName,
        NormalizedAddress normalizedAddress,
        GeocodeConfidence confidence,
        String provider
    ) {}
}
