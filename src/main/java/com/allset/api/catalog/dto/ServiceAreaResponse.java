package com.allset.api.catalog.dto;

import com.allset.api.integration.storage.dto.StorageRefResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ServiceAreaResponse(

        @Schema(description = "ID da área") UUID id,
        @Schema(description = "Nome da área") String name,
        @Schema(description = "Ícone da área (chave + URL pública)") StorageRefResponse icon,
        @Schema(description = "Área ativa") boolean active,
        @Schema(description = "Data de criação") Instant createdAt
) {}
