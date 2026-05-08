package com.allset.api.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seed", name = "production-enabled", havingValue = "true")
@Slf4j
public class ProductionSeedRunner implements ApplicationRunner {

    private final ProductionSeedService productionSeedService;

    @Override
    public void run(ApplicationArguments args) {
        ProductionSeedService.SeedResult result = productionSeedService.seed();

        if (result.skipped()) {
            log.info("event=production_seed skipped reason=admin-already-exists email={}", result.adminEmail());
            return;
        }

        log.info(
                "event=production_seed completed adminEmail={} areas={} categories={} plans={}",
                result.adminEmail(),
                result.areaCount(),
                result.categoryCount(),
                result.planCount()
        );
    }
}
