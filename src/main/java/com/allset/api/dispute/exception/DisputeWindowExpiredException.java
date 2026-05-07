package com.allset.api.dispute.exception;

public class DisputeWindowExpiredException extends RuntimeException {

    public DisputeWindowExpiredException() {
        super("A janela de 24h para abertura de disputa ja expirou");
    }
}
