package com.allset.api.geocoding.service;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
import com.allset.api.geocoding.exception.AddressNotGeocodableException;
import com.allset.api.geocoding.exception.GeocodingProviderUnavailableException;
import com.allset.api.geocoding.provider.GeocodingProvider;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeQuery;
import com.allset.api.geocoding.provider.GeocodingProvider.GeocodeResult;
import com.allset.api.shared.cache.CacheService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Orquestra cache (Redis) + provider externo de geocoding.
 *
 * <p>Estratégia:
 * <ol>
 *   <li>kill-switch {@code geocodingEnabled=false} → 503 imediato;</li>
 *   <li>cache hit positivo → devolve direto;</li>
 *   <li>cache hit negativo (marcador {@code NOT_FOUND}) → 422 sem chamar provider;</li>
 *   <li>cache miss → chama provider; resultado positivo é cacheado por {@code geocodingCacheTtlSeconds},
 *       resultado vazio por {@code geocodingNegativeCacheTtlSeconds}.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class GeocodingServiceImpl implements GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingServiceImpl.class);

    private static final String CACHE_PREFIX = "geocode:";
    private static final String NEGATIVE_MARKER = "NOT_FOUND";

    private final GeocodingProvider provider;
    private final CacheService cacheService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    public GeocodeResponse geocode(GeocodeRequest request) {
        if (Boolean.FALSE.equals(appProperties.geocodingEnabled())) {
            log.warn("Geocoding desabilitado via GEOCODING_ENABLED=false");
            throw new GeocodingProviderUnavailableException();
        }

        String cacheKey = CACHE_PREFIX + buildKey(request);

        Optional<String> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            String value = cached.get();
            if (NEGATIVE_MARKER.equals(value)) {
                log.debug("Cache hit negativo para chave={}", cacheKey);
                throw new AddressNotGeocodableException();
            }
            try {
                log.debug("Cache hit positivo para chave={}", cacheKey);
                return objectMapper.readValue(value, GeocodeResponse.class);
            } catch (JsonProcessingException ex) {
                log.warn("Falha ao deserializar cache de geocoding chave={}; ignorando entry", cacheKey, ex);
                cacheService.delete(cacheKey);
            }
        }

        Optional<GeocodeResult> result = provider.geocode(toQuery(request));

        if (result.isEmpty()) {
            cacheService.set(cacheKey, NEGATIVE_MARKER, appProperties.geocodingNegativeCacheTtlSeconds());
            log.info("Endereço não localizável cacheado provider={} chave={}", provider.name(), cacheKey);
            throw new AddressNotGeocodableException();
        }

        GeocodeResponse response = toResponse(result.get());
        try {
            cacheService.set(cacheKey, objectMapper.writeValueAsString(response),
                appProperties.geocodingCacheTtlSeconds());
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar resposta de geocoding para cache chave={}", cacheKey, ex);
        }
        return response;
    }

    private GeocodeQuery toQuery(GeocodeRequest request) {
        return GeocodeQuery.brazilianAddress(
            request.zipCode(),
            request.street(),
            request.number(),
            request.district(),
            request.city(),
            request.state()
        );
    }

    private GeocodeResponse toResponse(GeocodeResult result) {
        return new GeocodeResponse(
            result.lat(),
            result.lng(),
            result.displayName(),
            result.normalizedAddress(),
            result.confidence() != null ? result.confidence() : GeocodeConfidence.INTERPOLATED,
            result.provider() != null ? result.provider() : provider.name()
        );
    }

    /**
     * Chave estável para o cache. Inclui CEP para evitar colisão entre ruas
     * homônimas em CEPs distintos. SHA-256 mantém o tamanho fixo da chave Redis.
     */
    private static String buildKey(GeocodeRequest r) {
        String raw = String.join("|",
            normalize(r.zipCode()),
            normalize(r.street()),
            normalize(r.number()),
            normalize(r.city()),
            normalize(r.state())
        );
        return sha256Hex(raw);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível.", e);
        }
    }
}
