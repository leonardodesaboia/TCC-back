package com.allset.api.favorite.dto;

import com.allset.api.professional.dto.ProfessionalResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record FavoriteProfessionalResponse(
        @Schema(description = "ID do favorito") UUID id,
        @Schema(description = "ID do cliente") UUID clientId,
        @Schema(description = "Profissional favoritado") ProfessionalResponse professional,
        @Schema(description = "Data em que o favorito foi criado") Instant createdAt
) {
}
