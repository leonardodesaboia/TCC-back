package com.allset.api.calendar.exception;

import java.util.UUID;

public class BlockedPeriodNotFoundException extends RuntimeException {
    public BlockedPeriodNotFoundException(UUID id) {
        super("Período bloqueado não encontrado: " + id);
    }
}
