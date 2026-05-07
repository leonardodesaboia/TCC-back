package com.allset.api.notification.service;

import com.allset.api.notification.dto.PushTokenResponse;
import com.allset.api.notification.dto.RegisterPushTokenRequest;

import java.util.List;
import java.util.UUID;

public interface PushTokenService {

    PushTokenResponse register(UUID userId, RegisterPushTokenRequest request);

    List<PushTokenResponse> listForUser(UUID userId);

    void delete(UUID userId, UUID pushTokenId);

    long pruneInactiveTokens();
}
