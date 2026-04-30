package com.allset.api.catalog.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UpdateServiceAreaRequest(

        @Schema(description = "Nome da área", example = "Elétrica")
        @Size(max = 80, message = "Nome deve ter no máximo 80 caracteres")
        @NoHtml
        String name,

        @Schema(description = "Área ativa")
        Boolean active
) {}
