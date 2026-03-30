package com.allset.api.offering.exception;

import java.util.UUID;

public class ProfessionalOfferingNotFoundException extends RuntimeException {
    public ProfessionalOfferingNotFoundException(UUID id) {
        super("Serviço não encontrado: " + id);
    }
}
