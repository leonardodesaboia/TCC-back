package com.allset.api.shared.storage.exception;

import com.allset.api.shared.storage.domain.StorageBucket;

import java.util.Set;

public class InvalidFileTypeException extends RuntimeException {

    public InvalidFileTypeException(StorageBucket bucket, String detectedMime) {
        super("Tipo de arquivo não permitido para %s: '%s'. Tipos aceitos: %s"
                .formatted(bucket.name(), detectedMime, formatAllowed(bucket.allowedMimeTypes())));
    }

    private static String formatAllowed(Set<String> mimes) {
        return String.join(", ", mimes);
    }
}
