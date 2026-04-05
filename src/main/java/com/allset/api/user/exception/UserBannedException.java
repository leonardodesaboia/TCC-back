package com.allset.api.user.exception;

public class UserBannedException extends RuntimeException {
    public UserBannedException(String reason) {
        super("Usuário banido. Motivo: " + reason);
    }
}
