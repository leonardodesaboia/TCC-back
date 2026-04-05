package com.allset.api.auth.controller;

import com.allset.api.auth.dto.ForgotPasswordRequest;
import com.allset.api.auth.dto.LoginRequest;
import com.allset.api.auth.dto.RefreshTokenRequest;
import com.allset.api.auth.dto.ResetPasswordRequest;
import com.allset.api.auth.dto.TokenResponse;
import com.allset.api.auth.service.AuthService;
import com.allset.api.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticação", description = "Login, refresh de tokens, logout e redefinição de senha")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "Login",
        description = "Autentica o usuário com e-mail e senha. "
                    + "Retorna um **access token** de curta duração (Bearer) e um **refresh token** de longa duração. "
                    + "O access token deve ser enviado no header `Authorization: Bearer <token>` nas requisições subsequentes."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Autenticação bem-sucedida",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Dados inválidos (e-mail mal formatado, campos ausentes)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Credenciais inválidas (e-mail não encontrado ou senha incorreta)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Conta banida",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        ),
        @ApiResponse(
            responseCode = "423",
            description = "Conta em período de exclusão (grace period de 30 dias)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        )
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
        summary = "Refresh de tokens",
        description = "Substitui o par de tokens atual por um novo par. "
                    + "O refresh token anterior é **invalidado imediatamente** no Redis. "
                    + "Use este endpoint antes que o access token expire para manter a sessão ativa."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Tokens renovados com sucesso",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Refresh token ausente no corpo da requisição",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Refresh token inválido, expirado ou já utilizado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        )
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(
        summary = "Logout",
        description = "Revoga o refresh token no servidor, encerrando a sessão. "
                    + "O access token continua válido até sua expiração natural — o cliente deve descartá-lo. "
                    + "Retorna 204 mesmo se o token já estiver expirado."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Logout realizado com sucesso", content = @Content),
        @ApiResponse(
            responseCode = "400",
            description = "Refresh token ausente no corpo da requisição",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        )
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Esqueci minha senha",
        description = "Inicia o fluxo de redefinição de senha. "
                    + "Um código de **4 dígitos** é enviado para o e-mail informado caso a conta exista e esteja ativa. "
                    + "Por segurança, a resposta é sempre 204 independentemente de o e-mail existir ou não."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Solicitação processada (código enviado se o e-mail existir)", content = @Content),
        @ApiResponse(
            responseCode = "400",
            description = "E-mail ausente ou mal formatado",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        )
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Redefinir senha",
        description = "Conclui a redefinição de senha validando o código de 4 dígitos recebido por e-mail. "
                    + "Após a redefinição bem-sucedida, todas as sessões ativas são invalidadas — o usuário precisa fazer login novamente."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Senha redefinida com sucesso", content = @Content),
        @ApiResponse(
            responseCode = "400",
            description = "Código inválido/expirado, e-mail inválido ou nova senha muito curta",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))
        )
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
