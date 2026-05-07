package com.allset.api.geocoding.provider;

import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.NormalizedAddress;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.exception.GeocodingRateLimitException;
import com.allset.api.geocoding.provider.dto.BrasilApiCepResponse;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Provider especializado em CEP brasileiro usando
 * <a href="https://brasilapi.com.br/docs#tag/CEP-V2">BrasilAPI v2</a>.
 *
 * <p>Vantagens sobre o Nominatim para endereços brasileiros:
 * <ul>
 *   <li>cobertura muito maior de CEPs (agrega ViaCEP, OpenCEP, Postmon, WideNet);</li>
 *   <li>devolve rua e bairro normalizados em português;</li>
 *   <li>devolve coordenadas quando a fonte interna tem (open-cep, postmon, widenet).</li>
 * </ul>
 *
 * <p>Limitações:
 * <ul>
 *   <li>requer CEP no input — sem CEP, devolve {@link Optional#empty()};</li>
 *   <li>quando a fonte interna é {@code viacep} pode não vir {@code location} — também devolve empty;</li>
 *   <li>não geocodifica por número da casa — precisão é a do CEP.</li>
 * </ul>
 */
@Component
public class BrasilApiCepProvider implements GeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(BrasilApiCepProvider.class);

    private static final String PROVIDER_NAME = "brasilapi";
    private static final String BASE_URL = "https://brasilapi.com.br";
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final RestClient client;

    public BrasilApiCepProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        this.client = RestClient.builder()
            .baseUrl(BASE_URL)
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
        return lookup(query.zipCode())
            .filter(CepLookup::hasCoords)
            .map(this::toResult);
    }

    /**
     * Consulta a BrasilAPI e devolve o registro completo (endereço + coords se houver).
     * É público porque o {@code CompositeGeocodingProvider} também usa o endereço sem
     * coords para enriquecer a query do Nominatim.
     */
    public Optional<CepLookup> lookup(String zipCode) {
        String cep = stripCepMask(zipCode);
        if (cep == null || cep.length() != 8) {
            return Optional.empty();
        }

        String uri = "/api/cep/v2/" + cep;

        BrasilApiCepResponse response;
        try {
            response = client.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    HttpStatus status = HttpStatus.valueOf(res.getStatusCode().value());
                    if (status == HttpStatus.NOT_FOUND) {
                        return;
                    }
                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        throw new GeocodingRateLimitException();
                    }
                    log.error("BrasilAPI respondeu {} para uri={}", status, uri);
                    throw new GeocodingProviderUnavailableException();
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("BrasilAPI respondeu {} para uri={}", res.getStatusCode(), uri);
                    throw new GeocodingProviderUnavailableException();
                })
                .body(BrasilApiCepResponse.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.error("Falha HTTP ao chamar BrasilAPI status={} uri={}", ex.getStatusCode(), uri, ex);
            throw new GeocodingProviderUnavailableException(ex);
        } catch (ResourceAccessException ex) {
            log.warn("Timeout/IO ao chamar BrasilAPI uri={}: {}", uri, ex.getMessage());
            throw new GeocodingProviderUnavailableException(ex);
        }

        if (response == null) {
            return Optional.empty();
        }

        BigDecimal lat = parseCoord(response.location());
        BigDecimal lng = parseLng(response.location());

        return Optional.of(new CepLookup(
            mapNormalizedAddress(response, cep),
            buildDisplayName(response),
            lat,
            lng,
            response.service()
        ));
    }

    private GeocodeResult toResult(CepLookup lookup) {
        return new GeocodeResult(
            lookup.lat(),
            lookup.lng(),
            lookup.displayName(),
            lookup.address(),
            GeocodeConfidence.INTERPOLATED,
            PROVIDER_NAME
        );
    }

    /** Resultado completo da BrasilAPI, com ou sem coords. */
    public record CepLookup(
        NormalizedAddress address,
        String displayName,
        BigDecimal lat,
        BigDecimal lng,
        String service
    ) {
        public boolean hasCoords() {
            return lat != null && lng != null;
        }
    }

    private static BigDecimal parseCoord(BrasilApiCepResponse.Location location) {
        if (location == null || location.coordinates() == null) return null;
        return parseDecimal(location.coordinates().latitude());
    }

    private static BigDecimal parseLng(BrasilApiCepResponse.Location location) {
        if (location == null || location.coordinates() == null) return null;
        return parseDecimal(location.coordinates().longitude());
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim()).setScale(6, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String buildDisplayName(BrasilApiCepResponse r) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, r.street());
        appendIfPresent(sb, r.neighborhood());
        appendIfPresent(sb, r.city());
        appendIfPresent(sb, r.state());
        appendIfPresent(sb, formatCep(r.cep()));
        appendIfPresent(sb, "Brasil");
        return sb.toString();
    }

    private static NormalizedAddress mapNormalizedAddress(BrasilApiCepResponse r, String rawCep) {
        return new NormalizedAddress(
            blankToNull(r.street()),
            null,
            blankToNull(r.neighborhood()),
            blankToNull(r.city()),
            blankToNull(r.state()),
            formatCep(rawCep)
        );
    }

    private static String stripCepMask(String zipCode) {
        if (zipCode == null) return null;
        String digits = zipCode.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private static String formatCep(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 8) return raw;
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) return;
        if (sb.length() > 0) sb.append(", ");
        sb.append(value.trim());
    }
}
