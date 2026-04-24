package com.allset.api.notification.service;

import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.domain.PushToken;
import com.allset.api.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class LoggingNotificationPushDispatcher implements NotificationPushDispatcher {

    @Override
    public void dispatch(User user, Notification notification, List<PushToken> pushTokens) {
        log.info("event=push_dispatch notificationId={} userId={} type={} tokens={}",
                notification.getId(),
                user.getId(),
                notification.getType(),
                pushTokens.size());
    }
}
