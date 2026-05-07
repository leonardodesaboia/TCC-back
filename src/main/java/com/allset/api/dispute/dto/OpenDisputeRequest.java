package com.allset.api.dispute.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload para abertura de disputa")
public record OpenDisputeRequest(

        @Schema(description = "Motivo detalhado da disputa", example = "Servico nao foi realizado conforme combinado")
        @NotBlank(message = "Motivo da disputa e obrigatorio")
        @Size(max = 2000, message = "Motivo nao pode exceder 2000 caracteres")
        @NoHtml
        String reason
) {}
