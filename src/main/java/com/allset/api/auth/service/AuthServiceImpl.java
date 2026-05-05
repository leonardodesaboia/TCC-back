package com.allset.api.auth.service;

import com.allset.api.auth.dto.ForgotPasswordRequest;
import com.allset.api.auth.dto.LoginRequest;
import com.allset.api.auth.dto.RefreshTokenRequest;
import com.allset.api.auth.dto.ResetPasswordRequest;
import com.allset.api.auth.dto.TokenResponse;
import com.allset.api.auth.exception.InvalidCredentialsException;
import com.allset.api.auth.exception.InvalidResetCodeException;
import com.allset.api.auth.exception.InvalidTokenException;
import com.allset.api.config.AppProperties;
import com.allset.api.shared.cache.CacheService;
import com.allset.api.integration.email.EmailService;
import com.allset.api.shared.token.TokenParseException;
import com.allset.api.shared.token.TokenService;
import com.allset.api.user.domain.User;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.exception.UserPendingDeletionException;
import com.allset.api.user.exception.UserBannedException;
import com.allset.api.user.repository.UserRepository;
import com.allset.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String RESET_CODE_KEY_PREFIX = "reset_code:";

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final CacheService cacheService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (user.getDeletedAt() != null) {
            throw new UserPendingDeletionException(
                user.getDeletedAt().plus(30, ChronoUnit.DAYS)
            );
        }

        if (!user.isActive()) {
            throw new UserBannedException(user.getBanReason());
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return issueTokenPair(user.getId(), user.getRole().name());
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshTokenRequest request) {
        UUID userId = extractRefreshUserId(request.refreshToken());

        String storedToken = cacheService.get(REFRESH_KEY_PREFIX + userId)
            .orElseThrow(() -> new InvalidTokenException("Sessão expirada ou inválida"));

        if (!storedToken.equals(request.refreshToken())) {
            throw new InvalidTokenException("Refresh token inválido");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new InvalidTokenException("Usuário não encontrado ou removido"));

        if (!user.isActive()) {
            throw new InvalidTokenException("Conta banida");
        }

        cacheService.delete(REFRESH_KEY_PREFIX + userId);
        return issueTokenPair(user.getId(), user.getRole().name());
    }

    @Override
    public void logout(RefreshTokenRequest request) {
        try {
            UUID userId = tokenService.extractRefreshUserId(request.refreshToken());
            cacheService.delete(REFRESH_KEY_PREFIX + userId);
            log.info("Logout realizado para userId={}", userId);
        } catch (TokenParseException e) {
            // Token expirado ou inválido — Redis já expirou junto, nada a revogar
            log.debug("Logout com token inválido/expirado — ignorado: {}", e.getMessage());
        }
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        // Busca silenciosa — nunca revela se o e-mail existe
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            if (user.getDeletedAt() != null || !user.isActive()) {
                return; // Conta inativa ou deletada — ignora silenciosamente
            }

            String code = generateResetCode();
            long ttlSeconds = appProperties.resetCodeTtlMinutes() * 60L;
            cacheService.set(RESET_CODE_KEY_PREFIX + request.email(), code, ttlSeconds);

            emailService.sendPasswordResetCode(
                request.email(),
                code,
                appProperties.resetCodeTtlMinutes()
            );
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
            .filter(u -> u.getDeletedAt() == null && u.isActive())
            .orElseThrow(() -> new InvalidResetCodeException());

        String storedCode = cacheService.get(RESET_CODE_KEY_PREFIX + request.email())
            .orElseThrow(InvalidResetCodeException::new);

        if (!storedCode.equals(request.code())) {
            throw new InvalidResetCodeException();
        }

        cacheService.delete(RESET_CODE_KEY_PREFIX + request.email());

        // Invalida sessões ativas ao trocar de senha
        cacheService.delete(REFRESH_KEY_PREFIX + user.getId());

        userService.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()));

        log.info("Senha redefinida para userId={}", user.getId());
    }

    private TokenResponse issueTokenPair(UUID userId, String role) {
        String accessToken = tokenService.generateAccessToken(userId, role);
        String refreshToken = tokenService.generateRefreshToken(userId);

        long refreshTtlSeconds = appProperties.refreshTokenTtlDays() * 86_400L;
        cacheService.set(REFRESH_KEY_PREFIX + userId, refreshToken, refreshTtlSeconds);

        return TokenResponse.of(accessToken, refreshToken, tokenService.accessTokenTtlSeconds());
    }

    private UUID extractRefreshUserId(String token) {
        try {
            return tokenService.extractRefreshUserId(token);
        } catch (TokenParseException e) {
            throw new InvalidTokenException("Token inválido ou expirado");
        }
    }

    private String generateResetCode() {
        return String.format("%04d", secureRandom.nextInt(10_000));
    }
}
