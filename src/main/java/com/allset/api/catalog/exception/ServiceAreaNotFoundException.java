package com.allset.api.catalog.exception;

import java.util.UUID;

public class ServiceAreaNotFoundException extends RuntimeException {
    public ServiceAreaNotFoundException(UUID id) {
        super("Área de serviço não encontrada: " + id);
    }
}
