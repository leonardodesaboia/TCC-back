package com.allset.api.auth.exception;

/**
 * Lançada quando o código de redefinição de senha é inválido ou expirou.
 * Mapeada para HTTP 400 pelo {@code GlobalExceptionHandler}.
 */
public class InvalidResetCodeException extends RuntimeException {

    public InvalidResetCodeException() {
        super("Código inválido ou expirado");
    }
}
