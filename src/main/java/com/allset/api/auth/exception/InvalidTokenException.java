package com.allset.api.auth.exception;

/**
 * Lançada quando um token JWT é inválido, expirado ou não atende
 * aos requisitos esperados (ex: não é do tipo refresh).
 * Mapeada para HTTP 401 pelo {@code GlobalExceptionHandler}.
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
