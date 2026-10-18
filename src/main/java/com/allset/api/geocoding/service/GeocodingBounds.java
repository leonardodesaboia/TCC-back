package com.allset.api.geocoding.service;

import com.allset.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Rede de proteção contra coordenada absurda vinda do provider.
 *
 * <p>Motivo concreto: um CEP inexistente ({@code 99999-999}) devolveu 200 com um
 * ponto em Sarandi/PR. Sem esta checagem, o endereço seria salvo e o Express
 * abriria uma busca a 3 mil km do cliente.
 *
 * <p>A caixa vem de {@code GEOCODING_BOUNDING_BOX} e cobre todo o Ceará por padrão —
 * larga o bastante para nunca recusar um endereço legítimo da região atendida.
 * String vazia desliga a checagem.
 */
@Component
public class GeocodingBounds {

    private static final Logger log = LoggerFactory.getLogger(GeocodingBounds.class);

    private final boolean enabled;
    private final BigDecimal minLat;
    private final BigDecimal minLng;
    private final BigDecimal maxLat;
    private final BigDecimal maxLng;

    public GeocodingBounds(AppProperties appProperties) {
        String raw = appProperties.geocodingBoundingBox();
        String[] parts = raw == null ? new String[0] : raw.trim().split("\s*,\s*");

        if (parts.length != 4 || raw.isBlank()) {
            if (raw != null && !raw.isBlank()) {
                log.warn("GEOCODING_BOUNDING_BOX='{}' não tem 4 valores (minLat,minLng,maxLat,maxLng); "
                    + "checagem de limites desligada", raw);
            }
            this.enabled = false;
            this.minLat = this.minLng = this.maxLat = this.maxLng = null;
            return;
        }

        this.enabled = true;
        this.minLat = new BigDecimal(parts[0]);
        this.minLng = new BigDecimal(parts[1]);
        this.maxLat = new BigDecimal(parts[2]);
        this.maxLng = new BigDecimal(parts[3]);
    }

    /** {@code true} quando o ponto está dentro da caixa, ou quando a checagem está desligada. */
    public boolean contains(BigDecimal lat, BigDecimal lng) {
        if (!enabled) return true;
        if (lat == null || lng == null) return false;
        return lat.compareTo(minLat) >= 0 && lat.compareTo(maxLat) <= 0
            && lng.compareTo(minLng) >= 0 && lng.compareTo(maxLng) <= 0;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
