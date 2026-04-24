package com.allset.api.review.exception;

import java.util.UUID;

public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException(UUID orderId, UUID reviewerId) {
        super("Ja existe uma avaliacao para o pedido %s feita pelo usuario %s".formatted(orderId, reviewerId));
    }
}
