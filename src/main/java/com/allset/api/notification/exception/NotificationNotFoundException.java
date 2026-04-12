package com.allset.api.notification.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID notificationId) {
        super("Notificacao nao encontrada: " + notificationId);
    }
}
