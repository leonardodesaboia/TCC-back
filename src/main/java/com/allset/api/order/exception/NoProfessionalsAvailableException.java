package com.allset.api.order.exception;

public class NoProfessionalsAvailableException extends RuntimeException {
    public NoProfessionalsAvailableException() {
        super("Nenhum profissional disponível na sua região para esta categoria no momento");
    }
}
