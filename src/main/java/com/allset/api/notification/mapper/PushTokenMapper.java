package com.allset.api.notification.mapper;

import com.allset.api.notification.domain.PushToken;
import com.allset.api.notification.dto.PushTokenResponse;
import org.springframework.stereotype.Component;

@Component
public class PushTokenMapper {

    public PushTokenResponse toResponse(PushToken pushToken) {
        return new PushTokenResponse(
                pushToken.getId(),
                pushToken.getExpoToken(),
                pushToken.getPlatform(),
                pushToken.getCreatedAt(),
                pushToken.getLastSeen()
        );
    }
}
