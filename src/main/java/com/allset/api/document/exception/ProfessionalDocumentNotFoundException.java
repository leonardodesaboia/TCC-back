package com.allset.api.document.exception;

import java.util.UUID;

public class ProfessionalDocumentNotFoundException extends RuntimeException {
    public ProfessionalDocumentNotFoundException(UUID id) {
        super("Documento não encontrado: " + id);
    }
}
