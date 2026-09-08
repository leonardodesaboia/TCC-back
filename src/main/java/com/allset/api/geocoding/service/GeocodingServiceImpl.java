package com.allset.api.geocoding.service;

import com.allset.api.config.AppProperties;
import com.allset.api.geocoding.dto.GeocodeConfidence;
import com.allset.api.geocoding.dto.GeocodeRequest;
import com.allset.api.geocoding.dto.GeocodeResponse;
import com.allset.api.geocoding.dto.ReverseGeocodeRequest;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
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
 *
 * <p><b>O cache é otimização, não dependência.</b> Redis fora do ar degrada para
 * chamada direta ao provider — antes desta versão, o lookup ficava 60 segundos
 * pendurado e terminava em 500.
 */
@Service
@RequiredArgsConstructor
public class GeocodingServiceImpl implements GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingServiceImpl.class);

    private static final String CACHE_PREFIX = "geocode:";
    private static final String REVERSE_CACHE_PREFIX = "geocode:rev:";
    private static final String NEGATIVE_MARKER = "NOT_FOUND";

    /**
     * Casas decimais na chave do cache reverso. 5 casas ≈ 1 metro:
     * o suficiente para que arrastar o pin um pixel não gere chave nova, e fino
     * o bastante para não confundir dois endereços vizinhos.
     */
    private static final int REVERSE_KEY_SCALE = 5;

    private final GeocodingProvider provider;
    private final CacheService cacheService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final GeocodingBounds bounds;

    @Override
    public GeocodeResponse geocode(GeocodeRequest request) {
        requireEnabled();

        String cacheKey = CACHE_PREFIX + buildKey(request);

        Optional<GeocodeResponse> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        Optional<GeocodeResult> result = provider.geocode(toQuery(request));

        if (result.isPresent() && !bounds.contains(result.get().lat(), result.get().lng())) {
            log.warn("Provider devolveu ponto fora dos limites configurados (lat={}, lng={}) para cep={}; descartando",
                result.get().lat(), result.get().lng(), request.zipCode());
            result = Optional.empty();
        }

        if (result.isEmpty()) {
            writeCache(cacheKey, NEGATIVE_MARKER, appProperties.geocodingNegativeCacheTtlSeconds());
            log.info("Endereço não localizável cacheado provider={} chave={}", provider.name(), cacheKey);
            throw new AddressNotGeocodableException();
        }

        GeocodeResponse response = toResponse(result.get());
        writeCache(cacheKey, serialize(response), appProperties.geocodingCacheTtlSeconds());
        return response;
    }

    @Override
    public GeocodeResponse reverse(ReverseGeocodeRequest request) {
        requireEnabled();

        String cacheKey = REVERSE_CACHE_PREFIX + buildReverseKey(request.lat(), request.lng());

        Optional<GeocodeResponse> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            GeocodeResponse found = cached.get();
            // A chave é arredondada; o endereço pode ser reutilizado, o pin não.
            return new GeocodeResponse(request.lat(), request.lng(), found.displayName(),
                found.normalizedAddress(), found.confidence(), found.provider());
        }

        Optional<GeocodeResult> result = provider.reverse(request.lat(), request.lng());

        if (result.isEmpty()) {
            writeCache(cacheKey, NEGATIVE_MARKER, appProperties.geocodingNegativeCacheTtlSeconds());
            log.info("Ponto sem endereço reconhecível provider={} chave={}", provider.name(), cacheKey);
            throw new AddressNotGeocodableException();
        }

        // O ponto veio do usuário: devolvemos o endereço encontrado, mas mantemos a
        // coordenada que ele escolheu. Corrigir o pin por conta própria seria mover
        // o local do atendimento sem avisar.
        GeocodeResult found = result.get();
        GeocodeResponse response = new GeocodeResponse(
            request.lat(),
            request.lng(),
            found.displayName(),
            found.normalizedAddress(),
            found.confidence() != null ? found.confidence() : GeocodeConfidence.INTERPOLATED,
            found.provider() != null ? found.provider() : provider.name()
        );

        writeCache(cacheKey, serialize(response), appProperties.geocodingCacheTtlSeconds());
        return response;
    }

    // -------------------------------------------------------------------------

    private void requireEnabled() {
        if (Boolean.FALSE.equals(appProperties.geocodingEnabled())) {
            log.warn("Geocoding desabilitado via GEOCODING_ENABLED=false");
            throw new GeocodingProviderUnavailableException();
        }
    }

    /**
     * Lê o cache tolerando qualquer falha da infraestrutura. Redis indisponível vira
     * cache miss — o pedido segue mais lento, mas segue.
     *
     * @return resposta cacheada, ou vazio para miss (e para entry corrompida)
     * @throws AddressNotGeocodableException em hit negativo
     */
    private Optional<GeocodeResponse> readCache(String cacheKey) {
        Optional<String> cached;
        try {
            cached = cacheService.get(cacheKey);
        } catch (RuntimeException ex) {
            log.warn("Cache de geocoding indisponível na leitura ({}), seguindo direto para o provider",
                ex.getClass().getSimpleName());
            return Optional.empty();
        }

        if (cached.isEmpty()) {
            return Optional.empty();
        }

        String value = cached.get();
        if (NEGATIVE_MARKER.equals(value)) {
            log.debug("Cache hit negativo para chave={}", cacheKey);
            throw new AddressNotGeocodableException();
        }

        try {
            log.debug("Cache hit positivo para chave={}", cacheKey);
            return Optional.of(objectMapper.readValue(value, GeocodeResponse.class));
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao deserializar cache de geocoding chave={}; ignorando entry", cacheKey, ex);
            deleteCache(cacheKey);
            return Optional.empty();
        }
    }

    private void writeCache(String cacheKey, String value, long ttlSeconds) {
        if (value == null) return;
        try {
            cacheService.set(cacheKey, value, ttlSeconds);
        } catch (RuntimeException ex) {
            log.warn("Cache de geocoding indisponível na escrita ({}), resultado não foi memorizado",
                ex.getClass().getSimpleName());
        }
    }

    private void deleteCache(String cacheKey) {
        try {
            cacheService.delete(cacheKey);
        } catch (RuntimeException ex) {
            log.warn("Falha ao remover entry de cache chave={}", cacheKey);
        }
    }

    private String serialize(GeocodeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar resposta de geocoding para cache", ex);
            return null;
        }
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
     * homônimas em CEPs distintos, e bairro porque endereços que só diferem nele
     * são endereços diferentes. SHA-256 mantém o tamanho fixo da chave Redis.
     */
    private static String buildKey(GeocodeRequest r) {
        String raw = String.join("|",
            normalize(r.zipCode()),
            normalize(r.street()),
            normalize(r.number()),
            normalize(r.district()),
            normalize(r.city()),
            normalize(r.state())
        );
        return sha256Hex(raw);
    }

    /** Arredonda antes de montar a chave para que pixels vizinhos compartilhem cache. */
    private static String buildReverseKey(BigDecimal lat, BigDecimal lng) {
        return lat.setScale(REVERSE_KEY_SCALE, RoundingMode.HALF_UP).toPlainString()
            + "," + lng.setScale(REVERSE_KEY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Normaliza para que variações de digitação caiam na mesma chave: "Av. Dom Luís"
     * e "av. dom luis" são a mesma consulta e não devem bater duas vezes no provider.
     */
    private static String normalize(String value) {
        if (value == null) return "";
        String stripped = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.replaceAll("\\s+", " ");
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
