package com.allset.api.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ServiceAreaResponse(

        @Schema(description = "ID da área") UUID id,
        @Schema(description = "Nome da área") String name,
        @Schema(description = "URL do ícone") String iconUrl,
        @Schema(description = "Área ativa") boolean active,
        @Schema(description = "Data de criação") Instant createdAt
) {}
