package com.allset.api.notification.scheduler;

import com.allset.api.notification.service.PushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushTokenCleanupScheduler {

    private final PushTokenService pushTokenService;

    @Scheduled(cron = "${push-token-prune-cron}")
    public void pruneInactiveTokens() {
        long deletedCount = pushTokenService.pruneInactiveTokens();

        if (deletedCount == 0) {
            return;
        }

        log.info("event=push_token_cleanup_scheduler count={}", deletedCount);
    }
}
