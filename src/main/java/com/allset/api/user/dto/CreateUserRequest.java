package com.allset.api.user.dto;

import com.allset.api.shared.validation.ValidCPF;
import com.allset.api.shared.validation.NoHtml;
import com.allset.api.shared.validation.ValidPassword;
import com.allset.api.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

@Schema(description = "Dados para criação de um novo usuário")
public record CreateUserRequest(

    @Schema(description = "Nome completo", example = "João da Silva")
    @NotBlank(message = "Nome é obrigatório")
    @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
    @NoHtml
    String name,

    @Schema(description = "CPF do usuário (com ou sem máscara)", example = "529.982.247-25")
    @NotBlank(message = "CPF é obrigatório")
    @ValidCPF
    String cpf,

    @Schema(description = "Endereço de e-mail", example = "joao@email.com")
    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
    String email,

    @Schema(description = "Telefone no formato E.164", example = "+5511999999999")
    @NotBlank(message = "Telefone é obrigatório")
    @Pattern(
        regexp = "^\\+?[1-9]\\d{1,14}$",
        message = "Telefone inválido. Use formato E.164 (ex: +5511999999999)"
    )
    String phone,

    @Schema(description = "Data de nascimento", example = "1995-09-15")
    @NotNull(message = "Data de nascimento é obrigatória")
    @Past(message = "Data de nascimento deve estar no passado")
    LocalDate birthDate,

    @Schema(description = "Senha (mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial)", example = "Senha@2025!")
    @NotBlank(message = "Senha é obrigatória")
    @ValidPassword
    String password,

    @Schema(description = "Papel do usuário no sistema", example = "client")
    @NotNull(message = "Role é obrigatório")
    UserRole role

) {}
