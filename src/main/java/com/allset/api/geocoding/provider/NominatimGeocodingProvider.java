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
import java.net.URI;
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
 *   <li>1 req/s por IP — garantido pelo {@link NominatimRateLimiter} antes de cada
 *       requisição, e aliviado pelo cache do service.</li>
 * </ul>
 */
@Component
public class NominatimGeocodingProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodingProvider.class);

    private static final String PROVIDER_NAME = "nominatim";
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final RestClient client;
    private final NominatimRateLimiter rateLimiter;
    private final String baseUrl;

    public NominatimGeocodingProvider(AppProperties appProperties, NominatimRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.baseUrl = appProperties.geocodingBaseUrl();

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

    /**
     * Monta a URI absoluta do /search.
     *
     * <p>Precisa ser {@link URI} e não String: o {@code RestClient} trata String
     * como <i>template</i> e codifica de novo o que já veio codificado, virando
     * {@code %2520} no lugar do espaço. O efeito é silencioso e total — o Nominatim
     * responde 200 com lista vazia para todo endereço que tenha espaço no nome, ou
     * seja, todos. Só o /reverse escapava, porque manda apenas números.
     */
    private UriComponentsBuilder search() {
        return UriComponentsBuilder.fromUriString(baseUrl).path("/search");
    }

    @Override
    public Optional<GeocodeResult> geocode(GeocodeQuery query) {
        String countryCode = query.country() != null ? query.country() : "BR";

        // 1ª tentativa: busca estruturada (mais precisa quando o OSM tem todos os campos certos).
        GeocodeResult best = firstResult(call(buildStructuredUri(query, countryCode)));

        // Refinamento por bairro. A busca estruturada do Nominatim aceita street, city,
        // state e postalcode — não aceita bairro. Numa avenida que atravessa a cidade
        // isso deixa o ponto cair em qualquer trecho dela: na medição com 20 endereços
        // de Fortaleza, 8 caíram no bairro errado, um deles a 3,6 km do certo.
        //
        // A busca livre manda o bairro junto, e acertou 17 de 20 contra 12 de 20 da
        // estruturada. Onde as duas concordam, concordam no mesmo metro — então só
        // trocamos quando a livre cai no bairro que a pessoa informou.
        //
        // Por que importa mais do que parece: resultado impreciso faz o app exigir que
        // a pessoa toque no mapa, e o toque promove o ponto a `user_pin`, que o Express
        // aceita sem discutir. Abrir o mapa no trecho errado é o que transforma uma
        // sugestão ruim em coordenada confiável.
        if (shouldRefineByDistrict(best, query)) {
            best = refineByDistrict(best, query, countryCode);
        }

        // 2ª tentativa: busca livre (q=...). Mais tolerante a CEP ausente no OSM,
        // sigla de estado, prefixos como "Rua/Av." e variações de acentuação.
        if (best == null) {
            log.debug("Busca estruturada vazia, tentando busca livre");
            best = firstResult(call(buildFreeFormUri(query, countryCode)));
        }

        // 3ª tentativa: busca livre street-level — sem número e sem CEP. Cobre o caso comum
        // de prédios não mapeados no OSM (apartamentos), em que a precisão prédio é impossível
        // mas o centroide da rua é suficiente para o match Express de 300 m.
        if (best == null) {
            log.debug("Busca livre vazia, tentando street-level (sem número e sem CEP)");
            best = firstResult(call(buildStreetLevelUri(query, countryCode)));
        }

        return Optional.ofNullable(best);
    }

    /**
     * Só vale gastar a requisição extra quando há bairro para conferir e o resultado
     * não é do edifício. Precisão de edifício já está no lugar certo por definição.
     */
    private static boolean shouldRefineByDistrict(GeocodeResult structured, GeocodeQuery query) {
        return structured != null
            && structured.confidence() != GeocodeConfidence.ROOFTOP
            && isNotBlank(query.district());
    }

    private GeocodeResult refineByDistrict(GeocodeResult structured, GeocodeQuery query, String countryCode) {
        GeocodeResult candidate = firstResult(call(buildFreeFormUri(query, countryCode)));

        if (candidate == null || !districtMatches(candidate, query.district())) {
            return structured;
        }

        log.info("Refinamento por bairro trocou o resultado: estruturada caiu em '{}', busca livre em '{}'",
            districtOf(structured), districtOf(candidate));
        return candidate;
    }

    private static boolean districtMatches(GeocodeResult result, String requestedDistrict) {
        String found = districtOf(result);
        if (found == null) return false;
        return normalizeForCompare(found).equals(normalizeForCompare(requestedDistrict));
    }

    private static String districtOf(GeocodeResult result) {
        NormalizedAddress address = result == null ? null : result.normalizedAddress();
        return address == null ? null : address.district();
    }

    private static String normalizeForCompare(String value) {
        if (value == null) return "";
        return stripAccents(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private GeocodeResult firstResult(NominatimResponse[] results) {
        if (results == null || results.length == 0) return null;
        return mapResult(results[0]);
    }

    private NominatimResponse[] call(URI uri) {
        rateLimiter.acquire();
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

    /**
     * Reverse geocoding: ponto → endereço escrito.
     *
     * <p>{@code zoom=18} pede o nível de edifício/rua. Sem ele o Nominatim tende a
     * devolver o bairro, que é grosseiro demais para preencher um formulário.
     */
    @Override
    public Optional<GeocodeResult> reverse(BigDecimal lat, BigDecimal lng) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl).path("/reverse")
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", 1)
            .queryParam("zoom", 18)
            .queryParam("lat", lat.toPlainString())
            .queryParam("lon", lng.toPlainString())
            .build()
            .encode()
            .toUri();

        NominatimResponse single = callSingle(uri);
        if (single == null || single.lat() == null || single.lon() == null) {
            return Optional.empty();
        }
        return Optional.of(mapResult(single));
    }

    /** O /reverse devolve um objeto, não um array — daí a chamada separada. */
    private NominatimResponse callSingle(URI uri) {
        rateLimiter.acquire();
        try {
            return client.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatus status = HttpStatus.valueOf(res.getStatusCode().value());
                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        throw new GeocodingRateLimitException();
                    }
                    if (status == HttpStatus.NOT_FOUND) {
                        return;
                    }
                    log.error("Nominatim respondeu {} para uri={}", status, uri);
                    throw new GeocodingProviderUnavailableException();
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("Nominatim respondeu {} para uri={}", res.getStatusCode(), uri);
                    throw new GeocodingProviderUnavailableException();
                })
                .body(NominatimResponse.class);
        } catch (RestClientResponseException ex) {
            log.error("Falha HTTP ao chamar Nominatim status={} uri={}", ex.getStatusCode(), uri, ex);
            throw new GeocodingProviderUnavailableException(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Timeout/IO ao chamar Nominatim uri={}: {}", uri, ex.getMessage());
            throw new GeocodingProviderUnavailableException(ex);
        }
    }

    private URI buildStructuredUri(GeocodeQuery query, String countryCode) {
        return search()
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
            .toUri();
    }

    private URI buildFreeFormUri(GeocodeQuery query, String countryCode) {
        StringBuilder q = new StringBuilder();
        appendIfPresent(q, query.street());
        appendIfPresent(q, query.number());
        appendIfPresent(q, query.district());
        appendIfPresent(q, query.city());
        appendIfPresent(q, query.state());
        appendIfPresent(q, stripCepMask(query.zipCode()));

        return search()
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", 1)
            .queryParam("limit", 1)
            .queryParam("countrycodes", countryCode.toLowerCase(Locale.ROOT))
            .queryParam("q", stripAccents(q.toString().trim()))
            .build()
            .encode()
            .toUri();
    }

    private URI buildStreetLevelUri(GeocodeQuery query, String countryCode) {
        StringBuilder q = new StringBuilder();
        appendIfPresent(q, query.street());
        appendIfPresent(q, query.district());
        appendIfPresent(q, query.city());
        appendIfPresent(q, query.state());

        return search()
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", 1)
            .queryParam("limit", 1)
            .queryParam("countrycodes", countryCode.toLowerCase(Locale.ROOT))
            .queryParam("q", stripAccents(q.toString().trim()))
            .build()
            .encode()
            .toUri();
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
