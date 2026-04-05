package com.allset.api.subscription.exception;

import java.util.UUID;

public class SubscriptionPlanNotFoundException extends RuntimeException {
    public SubscriptionPlanNotFoundException(UUID id) {
        super("Plano de assinatura nao encontrado: " + id);
    }
}
