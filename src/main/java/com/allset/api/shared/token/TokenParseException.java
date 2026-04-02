package com.allset.api.shared.token;

/**
 * Lançada quando um token JWT não pode ser decodificado, está expirado
 * ou não atende aos requisitos esperados (ex: tipo inválido).
 */
public class TokenParseException extends RuntimeException {

    public TokenParseException(String message) {
        super(message);
    }

    public TokenParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
