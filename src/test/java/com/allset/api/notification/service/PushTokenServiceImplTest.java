package com.allset.api.notification.service;

import com.allset.api.notification.domain.Platform;
import com.allset.api.notification.domain.PushToken;
import com.allset.api.notification.dto.PushTokenResponse;
import com.allset.api.notification.dto.RegisterPushTokenRequest;
import com.allset.api.notification.mapper.PushTokenMapper;
import com.allset.api.notification.repository.PushTokenRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceImplTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PushTokenMapper pushTokenMapper;

    @InjectMocks
    private PushTokenServiceImpl pushTokenService;

    @Test
    void registerShouldCreateNewTokenWhenExpoTokenIsUnknown() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);

        PushToken saved = PushToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .expoToken("ExponentPushToken[token-1]")
                .platform(Platform.android)
                .createdAt(Instant.now())
                .lastSeen(Instant.now())
                .build();

        PushTokenResponse response = new PushTokenResponse(
                saved.getId(),
                saved.getExpoToken(),
                saved.getPlatform(),
                saved.getCreatedAt(),
                saved.getLastSeen()
        );

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(pushTokenRepository.findByExpoToken("ExponentPushToken[token-1]")).thenReturn(Optional.empty());
        when(pushTokenRepository.save(any(PushToken.class))).thenReturn(saved);
        when(pushTokenMapper.toResponse(saved)).thenReturn(response);

        PushTokenResponse result = pushTokenService.register(
                userId,
                new RegisterPushTokenRequest("ExponentPushToken[token-1]", Platform.android)
        );

        assertThat(result).isEqualTo(response);
    }

    @Test
    void registerShouldReuseExistingTokenAndMoveOwnershipWhenNeeded() {
        UUID userId = UUID.randomUUID();
        UUID oldUserId = UUID.randomUUID();
        User user = buildUser(userId);

        PushToken existing = PushToken.builder()
                .id(UUID.randomUUID())
                .userId(oldUserId)
                .expoToken("ExponentPushToken[token-1]")
                .platform(Platform.ios)
                .createdAt(Instant.now().minusSeconds(60))
                .lastSeen(Instant.now().minusSeconds(60))
                .build();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(pushTokenRepository.findByExpoToken("ExponentPushToken[token-1]")).thenReturn(Optional.of(existing));
        when(pushTokenRepository.save(existing)).thenReturn(existing);
        when(pushTokenMapper.toResponse(existing)).thenAnswer(invocation -> {
            PushToken token = invocation.getArgument(0);
            return new PushTokenResponse(
                    token.getId(),
                    token.getExpoToken(),
                    token.getPlatform(),
                    token.getCreatedAt(),
                    token.getLastSeen()
            );
        });

        PushTokenResponse result = pushTokenService.register(
                userId,
                new RegisterPushTokenRequest("ExponentPushToken[token-1]", Platform.android)
        );

        assertThat(existing.getUserId()).isEqualTo(userId);
        assertThat(existing.getPlatform()).isEqualTo(Platform.android);
        assertThat(result.platform()).isEqualTo(Platform.android);
    }

    @Test
    void pruneInactiveTokensShouldReturnDeletedCount() {
        when(pushTokenRepository.deleteByLastSeenBefore(any(Instant.class))).thenReturn(3L);

        long deletedCount = pushTokenService.pruneInactiveTokens();

        assertThat(deletedCount).isEqualTo(3L);
        verify(pushTokenRepository).deleteByLastSeenBefore(any(Instant.class));
    }

    private User buildUser(UUID userId) {
        User user = User.builder()
                .name("Usuario Teste")
                .cpf("12345678901")
                .cpfHash("b".repeat(64))
                .email("teste@example.com")
                .phone("85999999999")
                .password("senha-hash")
                .role(UserRole.client)
                .build();
        user.setId(userId);
        return user;
    }
}
