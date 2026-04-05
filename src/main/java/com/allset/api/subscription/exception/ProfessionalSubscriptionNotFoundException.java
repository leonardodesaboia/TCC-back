package com.allset.api.subscription.exception;

import java.util.UUID;

public class ProfessionalSubscriptionNotFoundException extends RuntimeException {
    public ProfessionalSubscriptionNotFoundException(UUID professionalId) {
        super("Profissional nao possui assinatura ativa: " + professionalId);
    }
}
