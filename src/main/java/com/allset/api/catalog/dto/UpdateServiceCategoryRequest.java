package com.allset.api.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateServiceCategoryRequest(

        @Schema(description = "Nome da categoria")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        String name,

        @Schema(description = "URL do ícone no S3")
        String iconUrl,

        @Schema(description = "Categoria ativa")
        Boolean active
) {}
