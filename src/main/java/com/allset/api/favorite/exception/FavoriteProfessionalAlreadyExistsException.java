package com.allset.api.favorite.exception;

import java.util.UUID;

public class FavoriteProfessionalAlreadyExistsException extends RuntimeException {

    public FavoriteProfessionalAlreadyExistsException(UUID clientId, UUID professionalId) {
        super("Profissional ja favoritado pelo cliente: clientId=%s, professionalId=%s"
                .formatted(clientId, professionalId));
    }
}
