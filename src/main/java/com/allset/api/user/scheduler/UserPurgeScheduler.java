package com.allset.api.user.scheduler;

import com.allset.api.user.domain.User;
import com.allset.api.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class UserPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserPurgeScheduler.class);
    private static final long GRACE_PERIOD_DAYS = 30;

    private final UserRepository userRepository;

    public UserPurgeScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Scheduled(cron = "${user-purge-cron}")
    @Transactional
    public void purgeExpiredAccounts() {
        Instant cutoff = Instant.now().minus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
        List<User> expired = userRepository.findAllByDeletedAtIsNotNullAndDeletedAtBefore(cutoff);

        if (expired.isEmpty()) {
            return;
        }

        userRepository.deleteAll(expired);
        log.info("event=user_purge count={} cutoff={}", expired.size(), cutoff);
    }
}
