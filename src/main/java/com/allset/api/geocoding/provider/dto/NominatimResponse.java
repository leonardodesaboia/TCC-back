package com.allset.api.geocoding.provider.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mapeamento do payload retornado pelo endpoint
 * {@code GET /search?format=jsonv2&addressdetails=1} do Nominatim.
 * Apenas os campos que o nosso provider consome são declarados;
 * o restante é ignorado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimResponse(

    String lat,
    String lon,

    @JsonProperty("display_name")
    String displayName,

    @JsonProperty("addresstype")
    String addressType,

    @JsonProperty("class")
    String osmClass,

    String type,

    Address address

) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(
        String road,
        @JsonProperty("house_number") String houseNumber,
        String suburb,
        String neighbourhood,
        @JsonProperty("city_district") String cityDistrict,
        String city,
        String town,
        String village,
        String municipality,
        String state,
        @JsonProperty("ISO3166-2-lvl4") String stateCode,
        String postcode,
        String country,
        @JsonProperty("country_code") String countryCode
    ) {}
}
