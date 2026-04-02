package com.allset.api.shared.token;

import java.util.UUID;

/**
 * Contrato agnóstico de framework para geração e validação de tokens de autenticação.
 * Implementações concretas podem usar JWT, PASETO, etc.
 */
public interface TokenService {

    /**
     * Gera um access token de curta duração contendo o ID e a role do usuário.
     *
     * @param userId UUID do usuário autenticado
     * @param role   role do usuário (ex: "admin", "client", "professional")
     * @return token de acesso assinado
     */
    String generateAccessToken(UUID userId, String role);

    /**
     * Gera um refresh token de longa duração para rotação de sessão.
     * O token carrega o claim {@code type=refresh} para distingui-lo do access token.
     *
     * @param userId UUID do usuário autenticado
     * @return refresh token assinado
     */
    String generateRefreshToken(UUID userId);

    /**
     * Decodifica e valida um refresh token, retornando o UUID do usuário proprietário.
     * Valida assinatura, expiração e a presença do claim {@code type=refresh}.
     *
     * @param token refresh token a ser validado
     * @return UUID do usuário extraído do claim {@code sub}
     * @throws TokenParseException se o token for inválido, expirado ou não for do tipo refresh
     */
    UUID extractRefreshUserId(String token);

    /**
     * Retorna o TTL configurado do access token em segundos.
     * Usado para popular o campo {@code expiresIn} da resposta de login/refresh.
     */
    long accessTokenTtlSeconds();
}
