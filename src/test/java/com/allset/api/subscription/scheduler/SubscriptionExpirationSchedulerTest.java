package com.allset.api.subscription.scheduler;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.repository.ProfessionalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationSchedulerTest {

    @Mock
    private ProfessionalRepository professionalRepository;

    @InjectMocks
    private SubscriptionExpirationScheduler subscriptionExpirationScheduler;

    @Test
    void clearExpiredSubscriptionsShouldCleanupExpiredPlans() {
        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(UUID.randomUUID());
        professional.setSubscriptionPlanId(UUID.randomUUID());
        professional.setSubscriptionExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

        when(professionalRepository.findAllBySubscriptionPlanIdIsNotNullAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of(professional));

        subscriptionExpirationScheduler.clearExpiredSubscriptions();

        ArgumentCaptor<List<Professional>> captor = ArgumentCaptor.forClass(List.class);
        verify(professionalRepository).saveAll(captor.capture());

        Professional savedProfessional = captor.getValue().getFirst();
        assertThat(savedProfessional.getSubscriptionPlanId()).isNull();
        assertThat(savedProfessional.getSubscriptionExpiresAt()).isNull();
    }

    @Test
    void clearExpiredSubscriptionsShouldDoNothingWhenThereIsNothingToCleanup() {
        when(professionalRepository.findAllBySubscriptionPlanIdIsNotNullAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());

        subscriptionExpirationScheduler.clearExpiredSubscriptions();

        verify(professionalRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
