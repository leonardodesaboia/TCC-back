package com.allset.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Requisição para iniciar o fluxo de redefinição de senha")
public record ForgotPasswordRequest(

    @Schema(description = "E-mail associado à conta", example = "joao@email.com")
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 150, message = "E-mail deve ter no maximo 150 caracteres")
    String email

) {}
