package com.allset.api.geocoding.exception;

/**
 * Lançada quando o provider devolve 429 (rate limit estourado).
 * Mapeada para HTTP 429.
 */
public class GeocodingRateLimitException extends RuntimeException {
    public GeocodingRateLimitException() {
        super("Limite de consultas de endereço atingido, tente novamente em instantes");
    }
}
