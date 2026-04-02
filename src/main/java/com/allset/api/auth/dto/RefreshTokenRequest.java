package com.allset.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Requisição para rotação de tokens")
public record RefreshTokenRequest(

    @Schema(description = "Refresh token JWT obtido no login ou na última rotação", example = "eyJhbGciOiJIUzI1NiJ9...")
    @NotBlank(message = "Refresh token é obrigatório")
    String refreshToken

) {}
