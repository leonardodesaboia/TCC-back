package com.allset.api.geocoding.service;

import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
import com.allset.api.geocoding.dto.ReverseGeocodeRequest;

public interface GeocodingService {

    /**
     * Resolve um endereço em coordenadas usando cache + provider.
     *
     * @throws com.allset.api.geocoding.exception.AddressNotGeocodableException
     *         quando o endereço não foi localizado (cache negativo, provider vazio,
     *         ou resultado fora dos limites geográficos configurados).
     * @throws com.allset.api.geocoding.exception.GeocodingProviderUnavailableException
     *         quando o provider falhou ou o módulo está desabilitado.
     * @throws com.allset.api.geocoding.exception.GeocodingRateLimitException
     *         quando o provider devolveu 429 ou a fila local estourou.
     */
    GeocodeResponse geocode(GeocodeRequest request);

    /**
     * Caminho inverso: converte um ponto do mapa em endereço escrito.
     *
     * <p>Usado quando a pessoa move o pin e o app precisa preencher rua e bairro
     * correspondentes ao ponto escolhido.
     *
     * @throws com.allset.api.geocoding.exception.AddressNotGeocodableException
     *         quando o provider não reconheceu nenhum endereço no ponto.
     */
    GeocodeResponse reverse(ReverseGeocodeRequest request);
}
