package com.allset.api.user.dto;

import com.allset.api.shared.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para banimento de um usuário")
public record BanUserRequest(

    @Schema(description = "Motivo do banimento", example = "Violação dos termos de uso: conteúdo impróprio")
    @NotBlank(message = "Motivo do banimento é obrigatório")
    @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
    @NoHtml
    String reason

) {}
