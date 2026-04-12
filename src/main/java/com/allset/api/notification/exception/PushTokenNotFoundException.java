package com.allset.api.notification.exception;

import java.util.UUID;

public class PushTokenNotFoundException extends RuntimeException {

    public PushTokenNotFoundException(UUID pushTokenId) {
        super("Token de push nao encontrado: " + pushTokenId);
    }
}
