package com.allset.api.geocoding.exception;

/**
 * Lançada quando o provider de geocoding não conseguiu localizar o endereço.
 * Mapeada para HTTP 422 no {@code GlobalExceptionHandler}.
 */
public class AddressNotGeocodableException extends RuntimeException {
    public AddressNotGeocodableException() {
        super("Endereço não localizável");
    }
}
