package com.allset.api.professional.exception;

import java.util.UUID;

public class ProfessionalNotFoundException extends RuntimeException {
    public ProfessionalNotFoundException(UUID id) {
        super("Profissional não encontrado: " + id);
    }
}
