package com.allset.api.notification.service;

import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.domain.PushToken;
import com.allset.api.notification.dto.NotificationPreferenceResponse;
import com.allset.api.notification.dto.NotificationResponse;
import com.allset.api.notification.dto.UpdateNotificationPreferenceRequest;
import com.allset.api.notification.exception.NotificationNotFoundException;
import com.allset.api.notification.mapper.NotificationMapper;
import com.allset.api.notification.repository.NotificationRepository;
import com.allset.api.notification.repository.PushTokenRepository;
import com.allset.api.user.domain.User;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationPushDispatcher notificationPushDispatcher;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listForUser(UUID userId, Pageable pageable) {
        requireActiveUser(userId);
        return notificationRepository.findAllByUserId(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        requireActiveUser(userId);

        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }

        return notificationMapper.toResponse(notification);
    }

    @Override
    public int markAllAsRead(UUID userId) {
        requireActiveUser(userId);
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreference(UUID userId) {
        User user = requireActiveUser(userId);
        return new NotificationPreferenceResponse(userId, user.isNotificationsEnabled());
    }

    @Override
    public NotificationPreferenceResponse updatePreference(UUID userId, UpdateNotificationPreferenceRequest request) {
        User user = requireActiveUser(userId);
        user.setNotificationsEnabled(request.notificationsEnabled());
        userRepository.save(user);

        return new NotificationPreferenceResponse(userId, user.isNotificationsEnabled());
    }

    @Override
    public void notifyUser(UUID userId, NotificationType type, String title, String body, JsonNode data) {
        Optional<User> userOptional = userRepository.findByIdAndDeletedAtIsNull(userId);
        if (userOptional.isEmpty()) {
            log.warn("event=notification_user_not_found userId={} type={}", userId, type);
            return;
        }

        User user = userOptional.get();

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .data(data)
                .createdAt(Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        dispatchIfPossible(user, saved);
    }

    @Override
    public void notifyUsers(List<UUID> userIds, NotificationType type, String title, String body, JsonNode data) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        LinkedHashSet<UUID> uniqueUserIds = new LinkedHashSet<>(userIds);
        for (UUID userId : uniqueUserIds) {
            notifyUser(userId, type, title, body, data);
        }
    }

    private void dispatchIfPossible(User user, Notification notification) {
        if (!user.isNotificationsEnabled()) {
            return;
        }

        List<PushToken> pushTokens = pushTokenRepository.findAllByUserId(user.getId());
        if (pushTokens.isEmpty()) {
            return;
        }

        try {
            notificationPushDispatcher.dispatch(user, notification, pushTokens);
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);
        } catch (Exception exception) {
            log.warn("event=push_dispatch_failed notificationId={} userId={} message={}",
                    notification.getId(),
                    user.getId(),
                    exception.getMessage());
        }
    }

    private User requireActiveUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
