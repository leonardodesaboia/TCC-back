package com.allset.api.notification.service;

import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.domain.Platform;
import com.allset.api.notification.domain.PushToken;
import com.allset.api.notification.dto.NotificationPreferenceResponse;
import com.allset.api.notification.dto.NotificationResponse;
import com.allset.api.notification.dto.UpdateNotificationPreferenceRequest;
import com.allset.api.notification.mapper.NotificationMapper;
import com.allset.api.notification.repository.NotificationRepository;
import com.allset.api.notification.repository.PushTokenRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.domain.UserRole;
import com.allset.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushTokenRepository pushTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationPushDispatcher notificationPushDispatcher;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void notifyUserShouldDispatchWhenUserAllowsPushAndHasToken() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User user = buildUser(userId, true);
        PushToken pushToken = PushToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .expoToken("ExponentPushToken[token-1]")
                .platform(Platform.android)
                .createdAt(Instant.now())
                .lastSeen(Instant.now())
                .build();

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(pushTokenRepository.findAllByUserId(userId)).thenReturn(List.of(pushToken));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                notification.setId(notificationId);
            }
            return notification;
        });

        notificationService.notifyUser(userId, NotificationType.new_message, "Nova mensagem", "Teste", null);

        verify(notificationPushDispatcher).dispatch(eq(user), any(Notification.class), anyList());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getLast().getSentAt()).isNotNull();
    }

    @Test
    void notifyUserShouldPersistButSkipDispatchWhenUserDisabledNotifications() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User user = buildUser(userId, false);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getId() == null) {
                notification.setId(notificationId);
            }
            return notification;
        });

        notificationService.notifyUser(userId, NotificationType.request_status_update, "Atualizacao", "Teste", null);

        verify(notificationPushDispatcher, never()).dispatch(any(), any(), any());
        verify(notificationRepository).save(any(Notification.class));
        verify(pushTokenRepository, never()).findAllByUserId(any());
    }

    @Test
    void markAsReadShouldUpdateNotificationWhenItIsUnread() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        User user = buildUser(userId, true);
        Notification notification = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type(NotificationType.new_message)
                .title("Nova mensagem")
                .body("Teste")
                .createdAt(Instant.now())
                .build();

        NotificationResponse response = new NotificationResponse(
                notificationId,
                NotificationType.new_message,
                "Nova mensagem",
                "Teste",
                null,
                null,
                Instant.now(),
                notification.getCreatedAt()
        );

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        NotificationResponse result = notificationService.markAsRead(userId, notificationId);

        assertThat(result).isEqualTo(response);
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void updatePreferenceShouldPersistNewFlag() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId, true);

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        NotificationPreferenceResponse response = notificationService.updatePreference(
                userId,
                new UpdateNotificationPreferenceRequest(false)
        );

        assertThat(response).isEqualTo(new NotificationPreferenceResponse(userId, false));
        assertThat(user.isNotificationsEnabled()).isFalse();
    }

    private User buildUser(UUID userId, boolean notificationsEnabled) {
        User user = User.builder()
                .name("Usuario Teste")
                .cpf("12345678901")
                .cpfHash("a".repeat(64))
                .email("teste@example.com")
                .phone("85999999999")
                .password("senha-hash")
                .role(UserRole.client)
                .notificationsEnabled(notificationsEnabled)
                .build();
        user.setId(userId);
        return user;
    }
}
