package com.allset.api.dispute.exception;

import com.allset.api.dispute.domain.DisputeStatus;

public class DisputeStatusTransitionException extends RuntimeException {

    public DisputeStatusTransitionException(DisputeStatus current, String operation) {
        super("Transicao invalida para a operacao '" + operation
                + "' a partir do status atual: " + current);
    }

    public DisputeStatusTransitionException(String message) {
        super(message);
    }
}
