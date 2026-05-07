package com.allset.api.catalog.dto;

import com.allset.api.integration.storage.dto.StorageRefResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ServiceCategoryResponse(

        @Schema(description = "ID da categoria") UUID id,
        @Schema(description = "ID da área pai") UUID areaId,
        @Schema(description = "Nome da categoria") String name,
        @Schema(description = "Ícone da categoria (chave + URL pública)") StorageRefResponse icon,
        @Schema(description = "Categoria ativa") boolean active,
        @Schema(description = "Data de criação") Instant createdAt
) {}
