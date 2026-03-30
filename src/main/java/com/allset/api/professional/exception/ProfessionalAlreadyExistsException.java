package com.allset.api.professional.exception;

import java.util.UUID;

public class ProfessionalAlreadyExistsException extends RuntimeException {
    public ProfessionalAlreadyExistsException(UUID userId) {
        super("Usuário já possui um perfil profissional: " + userId);
    }
}
