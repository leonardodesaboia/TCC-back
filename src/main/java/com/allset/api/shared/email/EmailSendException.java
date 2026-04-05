package com.allset.api.shared.email;

/**
 * Lançada quando o envio de e-mail falha por erro de infraestrutura (SMTP indisponível, etc.).
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
