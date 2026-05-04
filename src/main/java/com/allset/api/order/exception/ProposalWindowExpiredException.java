package com.allset.api.order.exception;

import java.util.UUID;

public class ProposalWindowExpiredException extends RuntimeException {
    public ProposalWindowExpiredException(UUID orderId) {
        super("Prazo para envio de propostas encerrado para o pedido: " + orderId);
    }
}
