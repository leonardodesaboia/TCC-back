package com.allset.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para atualização parcial de um usuário. Apenas os campos informados serão atualizados.")
public record UpdateUserRequest(

    @Schema(description = "Novo nome completo", example = "João Souza")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    String name,

    @Schema(description = "Novo endereço de e-mail", example = "joao.novo@email.com")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
    String email,

    @Schema(description = "Novo telefone no formato E.164", example = "+5511988887777")
    @Pattern(
        regexp = "^\\+?[1-9]\\d{1,14}$",
        message = "Telefone inválido. Use formato E.164 (ex: +5511999999999)"
    )
    String phone

) {}
