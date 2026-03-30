package com.allset.api.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateServiceCategoryRequest(

        @Schema(description = "ID da área pai", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "Área é obrigatória")
        UUID areaId,

        @Schema(description = "Nome da categoria", example = "Eletricista")
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        String name,

        @Schema(description = "URL do ícone no S3")
        String iconUrl
) {}
