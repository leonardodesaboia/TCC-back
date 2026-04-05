package com.allset.api.subscription.exception;

public class SubscriptionPlanNameAlreadyExistsException extends RuntimeException {
    public SubscriptionPlanNameAlreadyExistsException(String name) {
        super("Plano de assinatura ja cadastrado com o nome: " + name);
    }
}
