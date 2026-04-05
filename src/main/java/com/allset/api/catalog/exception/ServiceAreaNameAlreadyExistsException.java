package com.allset.api.catalog.exception;

public class ServiceAreaNameAlreadyExistsException extends RuntimeException {
    public ServiceAreaNameAlreadyExistsException(String name) {
        super("Área de serviço já cadastrada com o nome: " + name);
    }
}
