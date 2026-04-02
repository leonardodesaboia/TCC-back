package com.allset.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.allset.api.shared.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Requisição para redefinir a senha com o código de verificação")
public record ResetPasswordRequest(

    @Schema(description = "E-mail associado à conta", example = "joao@email.com")
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    @Schema(description = "Código de 4 dígitos recebido por e-mail", example = "4821")
    @NotBlank(message = "Código é obrigatório")
    @Pattern(regexp = "\\d{4}", message = "Código deve conter exatamente 4 dígitos numéricos")
    String code,

    @Schema(description = "Nova senha (mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial)", example = "NovaSenha@2025!")
    @NotBlank(message = "Nova senha é obrigatória")
    @ValidPassword
    String newPassword

) {}
