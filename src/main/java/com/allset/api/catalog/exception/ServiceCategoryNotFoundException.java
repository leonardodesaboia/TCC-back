package com.allset.api.catalog.exception;

import java.util.UUID;

public class ServiceCategoryNotFoundException extends RuntimeException {
    public ServiceCategoryNotFoundException(UUID id) {
        super("Categoria de serviço não encontrada: " + id);
    }
}
