package com.allset.api.document.dto;

import com.allset.api.document.domain.DocType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProfessionalDocumentRequest(

        @Schema(description = "Tipo do documento")
        @NotNull(message = "Tipo do documento é obrigatório")
        DocType docType,

        @Schema(description = "URL do arquivo no S3", example = "https://...")
        @NotBlank(message = "URL do arquivo é obrigatória")
        String fileUrl
) {}
