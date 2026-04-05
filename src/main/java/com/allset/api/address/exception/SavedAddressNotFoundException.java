package com.allset.api.address.exception;

import java.util.UUID;

public class SavedAddressNotFoundException extends RuntimeException {

    public SavedAddressNotFoundException(UUID id) {
        super("Endereço não encontrado: " + id);
    }
}
