package com.allset.api.dispute.dto;

import com.allset.api.dispute.domain.EvidenceType;
import com.allset.api.integration.storage.dto.StorageRefResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Representacao de uma evidencia de disputa")
public record DisputeEvidenceResponse(

        @Schema(description = "ID da evidencia")
        UUID id,

        @Schema(description = "ID da disputa associada")
        UUID disputeId,

        @Schema(description = "ID do usuario que enviou a evidencia (cliente, profissional ou admin)")
        UUID senderId,

        @Schema(description = "Tipo da evidencia")
        EvidenceType evidenceType,

        @Schema(description = "Conteudo textual — obrigatorio em text, opcional em photo (legenda)", nullable = true)
        String content,

        @Schema(description = "Referencia ao arquivo no storage — preenchido apenas em evidencias do tipo photo",
                nullable = true)
        StorageRefResponse file,

        @Schema(description = "Data/hora do envio")
        Instant sentAt
) {}
