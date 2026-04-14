package com.allset.api.payment.exception;

public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException() {
        super("Token do webhook inválido");
    }
}
