package com.allset.api.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FavoriteStatusResponse(
        @Schema(description = "ID do profissional") UUID professionalId,
        @Schema(description = "true quando o profissional esta favoritado pelo cliente autenticado") boolean favorite
) {
}
