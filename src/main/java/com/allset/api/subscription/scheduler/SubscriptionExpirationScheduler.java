package com.allset.api.subscription.scheduler;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.repository.ProfessionalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class SubscriptionExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpirationScheduler.class);

    private final ProfessionalRepository professionalRepository;

    public SubscriptionExpirationScheduler(ProfessionalRepository professionalRepository) {
        this.professionalRepository = professionalRepository;
    }

    @Scheduled(cron = "${subscription-expiration-cron}")
    @Transactional
    public void clearExpiredSubscriptions() {
        Instant now = Instant.now();
        List<Professional> expiredProfessionals = professionalRepository
                .findAllBySubscriptionPlanIdIsNotNullAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(now);

        if (expiredProfessionals.isEmpty()) {
            return;
        }

        for (Professional professional : expiredProfessionals) {
            professional.setSubscriptionPlanId(null);
            professional.setSubscriptionExpiresAt(null);
        }

        professionalRepository.saveAll(expiredProfessionals);
        log.info("event=subscription_expiration_cleanup count={} processedAt={}", expiredProfessionals.size(), now);
    }
}
