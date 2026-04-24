package com.allset.api.notification.service;

import com.allset.api.notification.domain.Notification;
import com.allset.api.notification.domain.PushToken;
import com.allset.api.user.domain.User;

import java.util.List;

public interface NotificationPushDispatcher {

    void dispatch(User user, Notification notification, List<PushToken> pushTokens);
}
