package com.allset.api.order.dto;

import com.allset.api.order.domain.PhotoType;
import com.allset.api.integration.storage.dto.StorageRefResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record OrderPhotoResponse(

        @Schema(description = "ID da foto") UUID id,
        @Schema(description = "Tipo da foto") PhotoType type,
        @Schema(description = "ID de quem fez upload") UUID uploaderId,
        @Schema(description = "Arquivo no storage (chave + URL pré-assinada)") StorageRefResponse file,
        @Schema(description = "Data e hora do upload") Instant uploadedAt
) {}
