package com.allset.api.notification.scheduler;

import com.allset.api.notification.service.PushTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushTokenCleanupSchedulerTest {

    @Mock
    private PushTokenService pushTokenService;

    @InjectMocks
    private PushTokenCleanupScheduler pushTokenCleanupScheduler;

    @Test
    void pruneInactiveTokensShouldCallService() {
        when(pushTokenService.pruneInactiveTokens()).thenReturn(2L);

        pushTokenCleanupScheduler.pruneInactiveTokens();

        verify(pushTokenService).pruneInactiveTokens();
    }
}
