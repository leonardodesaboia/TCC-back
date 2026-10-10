package com.allset.api.address.domain;

import com.allset.api.geocoding.dto.GeocodeConfidence;

/**
 * Decide se a coordenada de um endereço é boa o bastante para centrar o raio de
 * busca do Express.
 *
 * <p>Antes da V25 a checagem era "tem lat/lng?" — e o centroide do município
 * passava nela. A regra agora olha a procedência:
 *
 * <table>
 *   <tr><th>Origem</th><th>Express</th></tr>
 *   <tr><td>{@code device_gps}</td><td>aceita</td></tr>
 *   <tr><td>{@code user_pin}</td><td>aceita</td></tr>
 *   <tr><td>{@code geocoded}</td><td>aceita só com confiança {@code ROOFTOP}</td></tr>
 *   <tr><td>{@code legacy}</td><td>recusa</td></tr>
 *   <tr><td>{@code null}</td><td>recusa</td></tr>
 * </table>
 *
 * <p>Vive no domínio, e não no service do pedido, porque a listagem de endereços
 * precisa responder a mesma pergunta para sinalizar o que exige reconfirmação.
 */
public final class CoordinateTrust {

    private CoordinateTrust() {}

    public static boolean isExpressReady(SavedAddress address) {
        if (address == null) return false;
        if (address.getLat() == null || address.getLng() == null) return false;
        return isExpressReady(address.getCoordinateSource(), address.getCoordinateConfidence());
    }

    public static boolean isExpressReady(CoordinateSource source, GeocodeConfidence confidence) {
        if (source == null) return false;
        if (source.isUserConfirmed()) return true;
        if (source == CoordinateSource.geocoded) return confidence == GeocodeConfidence.ROOFTOP;
        return false;
    }
}
