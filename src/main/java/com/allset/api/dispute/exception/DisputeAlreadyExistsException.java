package com.allset.api.dispute.exception;

import java.util.UUID;

public class DisputeAlreadyExistsException extends RuntimeException {

    public DisputeAlreadyExistsException(UUID orderId) {
        super("Ja existe uma disputa para o pedido: " + orderId);
    }
}
