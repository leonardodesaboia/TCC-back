package com.allset.api.dispute.exception;

import java.util.UUID;

public class DisputeNotFoundException extends RuntimeException {

    public DisputeNotFoundException(UUID id) {
        super("Disputa nao encontrada: " + id);
    }

    public DisputeNotFoundException(String message) {
        super(message);
    }
}
