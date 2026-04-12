package com.allset.api.notification.mapper;

import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.dto.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getData(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
