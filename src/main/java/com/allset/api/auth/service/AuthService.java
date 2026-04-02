package com.allset.api.auth.service;

import com.allset.api.auth.dto.ForgotPasswordRequest;
import com.allset.api.auth.dto.LoginRequest;
import com.allset.api.auth.dto.RefreshTokenRequest;
import com.allset.api.auth.dto.ResetPasswordRequest;
import com.allset.api.auth.dto.TokenResponse;

public interface AuthService {

    /**
     * Autentica o usuário com e-mail e senha, retornando um par de tokens.
     *
     * @param request credenciais de login
     * @return access token + refresh token
     * @throws com.allset.api.auth.exception.InvalidCredentialsException se as credenciais forem inválidas
     * @throws com.allset.api.user.exception.UserPendingDeletionException se a conta estiver em período de graça
     * @throws com.allset.api.user.exception.UserBannedException se a conta estiver banida
     */
    TokenResponse login(LoginRequest request);

    /**
     * Rotaciona o par de tokens: invalida o refresh token atual no Redis e emite um novo par.
     *
     * @param request refresh token atual
     * @return novo par de tokens
     * @throws com.allset.api.auth.exception.InvalidTokenException se o token for inválido, expirado ou não for refresh
     */
    TokenResponse refresh(RefreshTokenRequest request);

    /**
     * Revoga o refresh token no Redis, encerrando a sessão do usuário.
     * Operação silenciosa: não falha se o token já estiver expirado ou ausente.
     *
     * @param request refresh token a ser revogado
     */
    void logout(RefreshTokenRequest request);

    /**
     * Inicia o fluxo de redefinição de senha enviando um código de 4 dígitos por e-mail.
     * Retorna silenciosamente mesmo se o e-mail não existir — previne enumeração de contas.
     *
     * @param request e-mail do usuário
     */
    void forgotPassword(ForgotPasswordRequest request);

    /**
     * Conclui a redefinição de senha validando o código e atualizando a senha.
     *
     * @param request e-mail, código de verificação e nova senha
     * @throws com.allset.api.auth.exception.InvalidResetCodeException se o código for inválido ou expirado
     */
    void resetPassword(ResetPasswordRequest request);
}
