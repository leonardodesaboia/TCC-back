package com.allset.api.subscription.exception;

import java.util.UUID;

public class SubscriptionPlanAlreadyActiveException extends RuntimeException {
    public SubscriptionPlanAlreadyActiveException(UUID professionalId, UUID subscriptionPlanId) {
        super("Profissional ja possui este plano ativo. professionalId=" + professionalId + ", subscriptionPlanId=" + subscriptionPlanId);
    }
}
