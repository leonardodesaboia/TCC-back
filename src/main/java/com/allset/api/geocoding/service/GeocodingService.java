package com.allset.api.geocoding.service;

import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;

public interface GeocodingService {

    /**
     * Resolve um endereço em coordenadas usando cache + provider.
     *
     * @throws com.allset.api.geocoding.exception.AddressNotGeocodableException
     *         quando o endereço não foi localizado (cache negativo ou provider vazio).
     * @throws com.allset.api.geocoding.exception.GeocodingProviderUnavailableException
     *         quando o provider falhou ou o módulo está desabilitado.
     * @throws com.allset.api.geocoding.exception.GeocodingRateLimitException
     *         quando o provider devolveu 429.
     */
    GeocodeResponse geocode(GeocodeRequest request);
}
