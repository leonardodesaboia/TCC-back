package com.allset.api.notification.service;

import com.allset.api.notification.domain.NotificationType;
import com.allset.api.notification.dto.NotificationPreferenceResponse;
import com.allset.api.notification.dto.NotificationResponse;
import com.allset.api.notification.dto.UpdateNotificationPreferenceRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    Page<NotificationResponse> listForUser(UUID userId, Pageable pageable);

    NotificationResponse markAsRead(UUID userId, UUID notificationId);

    int markAllAsRead(UUID userId);

    NotificationPreferenceResponse getPreference(UUID userId);

    NotificationPreferenceResponse updatePreference(UUID userId, UpdateNotificationPreferenceRequest request);

    void notifyUser(UUID userId, NotificationType type, String title, String body, JsonNode data);

    void notifyUsers(List<UUID> userIds, NotificationType type, String title, String body, JsonNode data);
}
