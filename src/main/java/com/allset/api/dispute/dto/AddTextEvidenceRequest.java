package com.allset.api.dispute.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para envio de evidencia textual")
public record AddTextEvidenceRequest(

        @Schema(description = "Conteudo textual da evidencia",
                example = "O servico apresentou problemas no acabamento da parede sul")
        @NotBlank(message = "Conteudo da evidencia e obrigatorio")
        @Size(max = 4000, message = "Conteudo nao pode exceder 4000 caracteres")
        String content
) {}
