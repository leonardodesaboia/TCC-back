package com.allset.api.payment.exception;

import com.allset.api.payment.domain.PaymentStatus;

public class PaymentStatusTransitionException extends RuntimeException {
    public PaymentStatusTransitionException(PaymentStatus current, String action) {
        super("Operação '" + action + "' não permitida para pagamento com status '" + current.name() + "'");
    }
}
