package com.allset.api.address.dto;

import com.allset.api.address.domain.CoordinateSource;
import com.allset.api.geocoding.dto.GeocodeConfidence;

import java.math.BigDecimal;

/**
 * Regras de coerência entre coordenada e procedência, compartilhadas pelos DTOs
 * de criação e atualização de endereço.
 *
 * <p>Existem em um único lugar porque os dois DTOs precisam responder igual —
 * a divergência entre eles é justamente como o problema antigo entrava no banco.
 */
final class CoordinateProvenanceRules {

    private CoordinateProvenanceRules() {}

    /** Coordenada e origem viajam juntas: ou os três campos vêm, ou nenhum vem. */
    static boolean sourceMatchesCoordinates(CoordinateSource source, BigDecimal lat, BigDecimal lng) {
        boolean hasCoords = lat != null && lng != null;
        boolean hasPartialCoords = (lat == null) != (lng == null);
        if (hasPartialCoords) return false;
        return hasCoords == (source != null);
    }

    /** {@code legacy} é marcação de backfill — só a migration atribui. */
    static boolean sourceIsClientAssignable(CoordinateSource source) {
        return source != CoordinateSource.legacy;
    }

    static boolean accuracyMatchesSource(CoordinateSource source, BigDecimal accuracyMeters) {
        if (accuracyMeters == null) return true;
        return source == CoordinateSource.device_gps;
    }

    static boolean confidenceMatchesSource(CoordinateSource source, GeocodeConfidence confidence) {
        if (confidence == null) return true;
        return source == CoordinateSource.geocoded;
    }
}
