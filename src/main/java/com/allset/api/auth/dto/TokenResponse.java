package com.allset.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Par de tokens retornado após autenticação ou refresh")
public record TokenResponse(

    @Schema(description = "Token de acesso JWT de curta duração (Bearer)", example = "eyJhbGciOiJIUzI1NiJ9...")
    String accessToken,

    @Schema(description = "Token de refresh JWT de longa duração, usado para obter novos tokens", example = "eyJhbGciOiJIUzI1NiJ9...")
    String refreshToken,

    @Schema(description = "Tipo do token", example = "Bearer")
    String tokenType,

    @Schema(description = "Tempo de expiração do access token em segundos", example = "900")
    long expiresIn

) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
