package com.allset.api.geocoding.exception;

/**
 * Lançada quando o provider de geocoding está fora do ar
 * (timeout, 5xx, kill-switch ativo). Mapeada para HTTP 503.
 */
public class GeocodingProviderUnavailableException extends RuntimeException {

    public GeocodingProviderUnavailableException() {
        super("Serviço de geocoding indisponível, tente novamente em instantes");
    }

    public GeocodingProviderUnavailableException(Throwable cause) {
        super("Serviço de geocoding indisponível, tente novamente em instantes", cause);
    }
}
