package com.allset.api.favorite.exception;

import java.util.UUID;

public class FavoriteProfessionalNotFoundException extends RuntimeException {

    public FavoriteProfessionalNotFoundException(UUID clientId, UUID professionalId) {
        super("Favorito nao encontrado para o cliente: clientId=%s, professionalId=%s"
                .formatted(clientId, professionalId));
    }
}
