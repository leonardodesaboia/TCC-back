package com.allset.api.user.exception;

import java.time.Instant;

public class UserPendingDeletionException extends RuntimeException {

    private final Instant scheduledDeletionAt;

    public UserPendingDeletionException(Instant scheduledDeletionAt) {
        super("Conta em período de exclusão. Você pode reativá-la até " + scheduledDeletionAt + ".");
        this.scheduledDeletionAt = scheduledDeletionAt;
    }

    public Instant getScheduledDeletionAt() {
        return scheduledDeletionAt;
    }
}
