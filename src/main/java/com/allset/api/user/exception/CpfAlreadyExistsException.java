package com.allset.api.user.exception;

public class CpfAlreadyExistsException extends RuntimeException {
    public CpfAlreadyExistsException() {
        super("CPF já cadastrado.");
    }
}
