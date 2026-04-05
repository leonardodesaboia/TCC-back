package com.allset.api.document.dto;

import com.allset.api.document.domain.DocType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ProfessionalDocumentResponse(

        @Schema(description = "ID do documento") UUID id,
        @Schema(description = "ID do profissional") UUID professionalId,
        @Schema(description = "Tipo do documento") DocType docType,
        @Schema(description = "URL do arquivo no S3") String fileUrl,
        @Schema(description = "Data de upload") Instant uploadedAt,
        @Schema(description = "Verificado pelo IDwall") boolean verified
) {}
