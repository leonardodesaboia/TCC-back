package com.allset.api.shared.storage.exception;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(long sizeBytes, long maxBytes) {
        super("Arquivo excede o limite permitido: %d bytes (máximo: %d bytes)"
                .formatted(sizeBytes, maxBytes));
    }
}
