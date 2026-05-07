package com.allset.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciais para autenticação na plataforma")
public record LoginRequest(

    @Schema(description = "E-mail cadastrado na plataforma", example = "joao@email.com")
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 150, message = "E-mail deve ter no maximo 150 caracteres")
    String email,

    @Schema(description = "Senha da conta", example = "Senha@2025!")
    @NotBlank(message = "Senha é obrigatória")
    @Size(max = 128, message = "Senha deve ter no maximo 128 caracteres")
    String password

) {}
