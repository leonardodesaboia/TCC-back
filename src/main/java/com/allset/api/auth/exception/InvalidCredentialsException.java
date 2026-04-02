package com.allset.api.auth.exception;

/**
 * Lançada quando as credenciais de login (e-mail/senha) são inválidas.
 * Mapeada para HTTP 401 pelo {@code GlobalExceptionHandler}.
 *
 * <p>A mensagem é genérica intencionalmente — não deve revelar se o e-mail
 * existe ou não na base, prevenindo enumeração de usuários.</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciais inválidas");
    }
}
