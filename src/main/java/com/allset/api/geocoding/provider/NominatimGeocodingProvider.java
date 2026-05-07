package com.allset.api.geocoding.provider;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.NormalizedAddress;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import com.allset.api.geocoding.provider.dto.NominatimResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Implementação default do {@link GeocodingProvider} usando o serviço público
 * do Nominatim (OpenStreetMap).
 *
 * <p>Política exigida pelo Nominatim:
 * <ul>
 *   <li>User-Agent identificador com contato — vem de {@code AppProperties.geocodingUserAgent};</li>
 *   <li>1 req/s por IP — defendido por cache no service (não aqui).</li>
 * </ul>
 */
@Component
public class NominatimGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingProvider.class);

    private static final String PROVIDER_NAME = "nominatim";
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final RestClient client;

    public NominatimGeocodingProvider(AppProperties appProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        this.client = RestClient.builder()
            .baseUrl(appProperties.geocodingBaseUrl())
            .defaultHeader("User-Agent", appProperties.geocodingUserAgent())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(factory)
            .build();
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<GeocodeResult> geocode(GeocodeQuery query) {
        String countryCode = query.country() != null ? query.country() : "BR";

        // 1ª tentativa: busca estruturada (mais precisa quando o OSM tem todos os campos certos).
        NominatimResponse[] results = call(buildStructuredUri(query, countryCode));

        // 2ª tentativa: busca livre (q=...). Mais tolerante a CEP ausente no OSM,
        // sigla de estado, prefixos como "Rua/Av." e variações de acentuação.
        if (results == null || results.length == 0) {
            log.debug("Busca estruturada vazia, tentando busca livre");
            results = call(buildFreeFormUri(query, countryCode));
        }

        // 3ª tentativa: busca livre street-level — sem número e sem CEP. Cobre o caso comum
        // de prédios não mapeados no OSM (apartamentos), em que a precisão prédio é impossível
        // mas o centroide da rua é suficiente para o match Express de 300 m.
        if (results == null || results.length == 0) {
            log.debug("Busca livre vazia, tentando street-level (sem número e sem CEP)");
            results = call(buildStreetLevelUri(query, countryCode));
        }

        if (results == null || results.length == 0) {
            return Optional.empty();
        }

        return Optional.of(mapResult(results[0]));
    }

    private NominatimResponse[] call(String uri) {
        try {
            return client.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatus status = HttpStatus.valueOf(res.getStatusCode().value());
                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        throw new GeocodingRateLimitException();
                    }
                    log.error("Nominatim respondeu {} para uri={}", status, uri);
                    throw new GeocodingProviderUnavailableException();
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("Nominatim respondeu {} para uri={}", res.getStatusCode(), uri);
                    throw new GeocodingProviderUnavailableException();
                })
                .body(NominatimResponse[].class);
        } catch (RestClientResponseException ex) {
            log.error("Falha HTTP ao chamar Nominatim status={} uri={}", ex.getStatusCode(), uri, ex);
            throw new GeocodingProviderUnavailableException(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Timeout/IO ao chamar Nominatim uri={}: {}", uri, ex.getMessage());
            throw new GeocodingProviderUnavailableException(ex);
        }
    }

    private String buildStructuredUri(GeocodeQuery query, String countryCode) {
        return UriComponentsBuilder.fromPath("/search")
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", 1)
            .queryParam("limit", 1)
            .queryParam("countrycodes", countryCode.toLowerCase(Locale.ROOT))
            .queryParam("street", stripAccents(buildStreetParam(query.number(), query.street())))
            .queryParam("city", stripAccents(query.city() != null ? query.city() : ""))
            .queryParam("state", stripAccents(query.state() != null ? query.state() : ""))
            .queryParam("postalcode", stripCepMask(query.zipCode()))
            .build()
            .encode()
            .toUriString();
    }

    private String buildFreeFormUri(GeocodeQuery query, String countryCode) {
        StringBuilder q = new StringBuilder();
        appendIfPresent(q, query.street());
        appendIfPresent(q, query.number());
        appendIfPresent(q, query.district());
        appendIfPresent(q, query.city());
        appendIfPresent(q, query.state());
        appendIfPresent(q, stripCepMask(query.zipCode()));

        return UriComponentsBuilder.fromPath("/search")
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", 1)
            .queryParam("limit", 1)
            .queryParam("countrycodes", countryCode.toLowerCase(Locale.ROOT))
            .queryParam("q", stripAccents(q.toString().trim()))
            .build()
            .encode()
            .toUriString();
    }

    private String buildStreetLevelUri(GeocodeQuery query, String countryCode) {
        StringBuilder q = new StringBuilder();
        appendIfPresent(q, query.street());
        appendIfPresent(q, query.district());
        appendIfPresent(q, query.city());
        appendIfPresent(q, query.state());

        return UriComponentsBuilder.fromPath("/search")
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", 1)
            .queryParam("limit", 1)
            .queryParam("countrycodes", countryCode.toLowerCase(Locale.ROOT))
            .queryParam("q", stripAccents(q.toString().trim()))
            .build()
            .encode()
            .toUriString();
    }

    /**
     * Remove diacríticos para contornar inconsistência do índice de texto do Nominatim
     * com português acentuado — "Joaquim Távora" não casa, "Joaquim Tavora" casa.
     * Não muda o resultado retornado: o response do Nominatim continua acentuado.
     */
    private static String stripAccents(String value) {
        if (value == null || value.isEmpty()) return value;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(value.trim());
    }

    private static String buildStreetParam(String number, String street) {
        if (street == null || street.isBlank()) return "";
        if (number == null || number.isBlank()) return street;
        return number + " " + street;
    }

    private static String stripCepMask(String zipCode) {
        if (zipCode == null) return "";
        return zipCode.replace("-", "").trim();
    }

    private GeocodeResult mapResult(NominatimResponse r) {
        BigDecimal lat = r.lat() != null ? new BigDecimal(r.lat()).setScale(6, java.math.RoundingMode.HALF_UP) : null;
        BigDecimal lng = r.lon() != null ? new BigDecimal(r.lon()).setScale(6, java.math.RoundingMode.HALF_UP) : null;

        NormalizedAddress normalized = mapNormalizedAddress(r);
        GeocodeConfidence confidence = deriveConfidence(r);

        return new GeocodeResult(lat, lng, r.displayName(), normalized, confidence, PROVIDER_NAME);
    }

    private static NormalizedAddress mapNormalizedAddress(NominatimResponse r) {
        NominatimResponse.Address a = r.address();
        if (a == null) {
            return new NormalizedAddress(null, null, null, null, null, null);
        }
        return new NormalizedAddress(
            a.road(),
            a.houseNumber(),
            firstNonBlank(a.suburb(), a.neighbourhood(), a.cityDistrict()),
            firstNonBlank(a.city(), a.town(), a.village(), a.municipality()),
            extractStateCode(a.stateCode()),
            normalizeCep(a.postcode())
        );
    }

    private static GeocodeConfidence deriveConfidence(NominatimResponse r) {
        String type = r.addressType();
        if (type == null) return GeocodeConfidence.INTERPOLATED;
        return switch (type) {
            case "building", "house", "amenity", "shop", "office" -> GeocodeConfidence.ROOFTOP;
            case "road", "street" -> GeocodeConfidence.INTERPOLATED;
            case "neighbourhood", "suburb", "quarter", "city_district",
                 "city", "town", "village", "hamlet", "municipality",
                 "state", "country" -> GeocodeConfidence.CITY;
            default -> GeocodeConfidence.INTERPOLATED;
        };
    }

    /** Extrai a sigla do estado a partir do código ISO 3166-2 (ex: "BR-CE" -> "CE"). */
    private static String extractStateCode(String iso) {
        if (iso == null || iso.isBlank()) return null;
        int dash = iso.indexOf('-');
        return dash >= 0 && dash + 1 < iso.length() ? iso.substring(dash + 1) : iso;
    }

    /** Aplica máscara 99999-999 quando possível. */
    private static String normalizeCep(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 8) return raw;
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }

    private static String firstNonBlank(String... values) {
        List<String> candidates = Arrays.asList(values);
        return candidates.stream()
            .filter(v -> v != null && !v.isBlank())
            .findFirst()
            .orElse(null);
    }
}
