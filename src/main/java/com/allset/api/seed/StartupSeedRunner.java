package com.allset.api.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seed", name = "enabled", havingValue = "true")
@Slf4j
public class StartupSeedRunner implements ApplicationRunner {

    private final StartupSeedService startupSeedService;

    @Override
    public void run(ApplicationArguments args) {
        StartupSeedService.SeedResult result = startupSeedService.seed();

        if (result.skipped()) {
            log.info("event=startup_seed skipped reason=marker-user-exists email={}", result.adminEmail());
            return;
        }

        log.info(
                "event=startup_seed completed users={} professionals={} orders={} disputes={} conversations={} password={} adminEmail={} clientEmail={}",
                result.userCount(),
                result.professionalCount(),
                result.orderCount(),
                result.disputeCount(),
                result.conversationCount(),
                result.defaultPassword(),
                result.adminEmail(),
                result.clientEmail()
        );
    }
}
