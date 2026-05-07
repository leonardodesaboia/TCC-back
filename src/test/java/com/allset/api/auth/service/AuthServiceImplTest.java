package com.allset.api.auth.service;

import com.allset.api.auth.dto.ForgotPasswordRequest;
import com.allset.api.auth.dto.LoginRequest;
import com.allset.api.auth.dto.RefreshTokenRequest;
import com.allset.api.auth.dto.ResetPasswordRequest;
import com.allset.api.auth.dto.TokenResponse;
import com.allset.api.config.AppProperties;
import com.allset.api.shared.cache.CacheService;
import com.allset.api.integration.email.EmailService;
import com.allset.api.shared.token.TokenService;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import com.allset.api.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private CacheService cacheService;

    @Mock
    private EmailService emailService;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginShouldIssueTokensAndPersistRefreshToken() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "cliente@example.com", UserRole.client);

        when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Senha@2025!", user.getPassword())).thenReturn(true);
        when(tokenService.generateAccessToken(userId, "client")).thenReturn("access-token");
        when(tokenService.generateRefreshToken(userId)).thenReturn("refresh-token");
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);
        when(appProperties.refreshTokenTtlDays()).thenReturn(7);

        TokenResponse response = authService.login(new LoginRequest("cliente@example.com", "Senha@2025!"));

        assertThat(response).isEqualTo(new TokenResponse("access-token", "refresh-token", "Bearer", 900L));
        verify(cacheService).set("refresh:" + userId, "refresh-token", 604800L);
    }

    @Test
    void refreshShouldRotateTokensWhenStoredTokenMatches() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "cliente@example.com", UserRole.client);

        when(tokenService.extractRefreshUserId("refresh-antigo")).thenReturn(userId);
        when(cacheService.get("refresh:" + userId)).thenReturn(Optional.of("refresh-antigo"));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(tokenService.generateAccessToken(userId, "client")).thenReturn("novo-access");
        when(tokenService.generateRefreshToken(userId)).thenReturn("novo-refresh");
        when(tokenService.accessTokenTtlSeconds()).thenReturn(900L);
        when(appProperties.refreshTokenTtlDays()).thenReturn(7);

        TokenResponse response = authService.refresh(new RefreshTokenRequest("refresh-antigo"));

        assertThat(response.accessToken()).isEqualTo("novo-access");
        assertThat(response.refreshToken()).isEqualTo("novo-refresh");
        verify(cacheService).delete("refresh:" + userId);
        verify(cacheService).set("refresh:" + userId, "novo-refresh", 604800L);
    }

    @Test
    void forgotPasswordShouldStoreCodeAndSendEmailForActiveUser() {
        User user = activeUser(UUID.randomUUID(), "cliente@example.com", UserRole.client);

        when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));
        when(appProperties.resetCodeTtlMinutes()).thenReturn(10);

        authService.forgotPassword(new ForgotPasswordRequest("cliente@example.com"));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheService).set(eq("reset_code:cliente@example.com"), codeCaptor.capture(), eq(600L));
        verify(emailService).sendPasswordResetCode("cliente@example.com", codeCaptor.getValue(), 10);
        assertThat(codeCaptor.getValue()).hasSize(4).containsOnlyDigits();
    }

    @Test
    void resetPasswordShouldUpdatePasswordAndInvalidateSession() {
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, "cliente@example.com", UserRole.client);

        when(userRepository.findByEmail("cliente@example.com")).thenReturn(Optional.of(user));
        when(cacheService.get("reset_code:cliente@example.com")).thenReturn(Optional.of("1234"));
        when(passwordEncoder.encode("NovaSenha@2025!")).thenReturn("senha-criptografada");

        authService.resetPassword(new ResetPasswordRequest("cliente@example.com", "1234", "NovaSenha@2025!"));

        verify(cacheService).delete("reset_code:cliente@example.com");
        verify(cacheService).delete("refresh:" + userId);
        verify(userService).updatePassword(userId, "senha-criptografada");
    }

    private User activeUser(UUID id, String email, UserRole role) {
        User user = User.builder()
                .name("Usuario Teste")
                .cpf("52998224725")
                .cpfHash("a".repeat(64))
                .email(email)
                .phone("+5585999999999")
                .password("senha-criptografada")
                .role(role)
                .active(true)
                .build();
        user.setId(id);
        return user;
    }
}
