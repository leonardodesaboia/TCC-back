package com.allset.api.payment.exception;

import java.util.UUID;

public class PaymentAlreadyExistsException extends RuntimeException {
    public PaymentAlreadyExistsException(UUID orderId) {
        super("Já existe um pagamento ativo para o pedido: " + orderId);
    }
}
