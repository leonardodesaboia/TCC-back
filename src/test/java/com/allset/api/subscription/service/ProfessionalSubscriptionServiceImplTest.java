package com.allset.api.subscription.service;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.dto.AssignSubscriptionPlanRequest;
import com.allset.api.subscription.dto.CancelSubscriptionResponse;
import com.allset.api.subscription.dto.ProfessionalSubscriptionResponse;
import com.allset.api.subscription.exception.ProfessionalSubscriptionNotFoundException;
import com.allset.api.subscription.exception.SubscriptionPlanAlreadyActiveException;
import com.allset.api.subscription.exception.SubscriptionPlanNotFoundException;
import com.allset.api.subscription.mapper.ProfessionalSubscriptionMapper;
import com.allset.api.subscription.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfessionalSubscriptionServiceImplTest {

    @Mock
    private ProfessionalRepository professionalRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private ProfessionalSubscriptionMapper professionalSubscriptionMapper;

    @InjectMocks
    private ProfessionalSubscriptionServiceImpl professionalSubscriptionService;

    @Test
    void assignPlanShouldCreateNewMonthlyCycleWhenProfessionalHasNoActivePlan() {
        UUID professionalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Plano Pro")
                .priceMonthly(new BigDecimal("49.90"))
                .highlightInSearch(true)
                .expressPriority(true)
                .badgeLabel("Pro")
                .active(true)
                .build();
        plan.setId(planId);

        ProfessionalSubscriptionResponse response = new ProfessionalSubscriptionResponse(
                professionalId,
                planId,
                "Plano Pro",
                new BigDecimal("49.90"),
                true,
                true,
                "Pro",
                Instant.now().plus(30, ChronoUnit.DAYS),
                true,
                null
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(planId)).thenReturn(Optional.of(plan));
        when(professionalRepository.save(professional)).thenReturn(professional);
        when(professionalSubscriptionMapper.toResponse(professional, plan)).thenReturn(response);

        ProfessionalSubscriptionResponse result = professionalSubscriptionService.assignPlan(
                professionalId,
                new AssignSubscriptionPlanRequest(planId)
        );

        assertThat(result).isEqualTo(response);
        assertThat(professional.getSubscriptionPlanId()).isEqualTo(planId);
        assertThat(professional.getSubscriptionExpiresAt()).isNotNull();
        assertThat(professional.getSubscriptionExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void assignPlanShouldChangePlanAndKeepCurrentExpiration() {
        UUID professionalId = UUID.randomUUID();
        UUID currentPlanId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();
        Instant currentExpiration = Instant.now().plus(10, ChronoUnit.DAYS);

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);
        professional.setSubscriptionPlanId(currentPlanId);
        professional.setSubscriptionExpiresAt(currentExpiration);

        SubscriptionPlan newPlan = SubscriptionPlan.builder()
                .name("Plano Premium")
                .priceMonthly(new BigDecimal("69.90"))
                .highlightInSearch(true)
                .expressPriority(true)
                .badgeLabel("Premium")
                .active(true)
                .build();
        newPlan.setId(newPlanId);

        ProfessionalSubscriptionResponse response = new ProfessionalSubscriptionResponse(
                professionalId,
                newPlanId,
                "Plano Premium",
                new BigDecimal("69.90"),
                true,
                true,
                "Premium",
                currentExpiration,
                true,
                null
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(newPlanId)).thenReturn(Optional.of(newPlan));
        when(professionalRepository.save(professional)).thenReturn(professional);
        when(professionalSubscriptionMapper.toResponse(professional, newPlan)).thenReturn(response);

        ProfessionalSubscriptionResponse result = professionalSubscriptionService.assignPlan(
                professionalId,
                new AssignSubscriptionPlanRequest(newPlanId)
        );

        assertThat(result).isEqualTo(response);
        assertThat(professional.getSubscriptionPlanId()).isEqualTo(newPlanId);
        assertThat(professional.getSubscriptionExpiresAt()).isEqualTo(currentExpiration);
    }

    @Test
    void assignPlanShouldRejectWhenSamePlanIsAlreadyActive() {
        UUID professionalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);
        professional.setSubscriptionPlanId(planId);
        professional.setSubscriptionExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Plano Pro")
                .priceMonthly(new BigDecimal("49.90"))
                .active(true)
                .build();
        plan.setId(planId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> professionalSubscriptionService.assignPlan(
                professionalId,
                new AssignSubscriptionPlanRequest(planId)
        ))
                .isInstanceOf(SubscriptionPlanAlreadyActiveException.class)
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void assignPlanShouldRejectInactiveOrMissingTargetPlan() {
        UUID professionalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalSubscriptionService.assignPlan(
                professionalId,
                new AssignSubscriptionPlanRequest(planId)
        ))
                .isInstanceOf(SubscriptionPlanNotFoundException.class)
                .hasMessageContaining(planId.toString());
    }

    @Test
    void findCurrentShouldRejectWhenProfessionalHasNoActiveSubscription() {
        UUID professionalId = UUID.randomUUID();
        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));

        assertThatThrownBy(() -> professionalSubscriptionService.findCurrent(professionalId))
                .isInstanceOf(ProfessionalSubscriptionNotFoundException.class)
                .hasMessageContaining(professionalId.toString());
    }

    @Test
    void findCurrentShouldClearExpiredSubscriptionBeforeFailing() {
        UUID professionalId = UUID.randomUUID();
        UUID oldPlanId = UUID.randomUUID();

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);
        professional.setSubscriptionPlanId(oldPlanId);
        professional.setSubscriptionExpiresAt(Instant.now().minus(2, ChronoUnit.DAYS));

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(professionalRepository.save(professional)).thenReturn(professional);

        assertThatThrownBy(() -> professionalSubscriptionService.findCurrent(professionalId))
                .isInstanceOf(ProfessionalSubscriptionNotFoundException.class)
                .hasMessageContaining(professionalId.toString());

        assertThat(professional.getSubscriptionPlanId()).isNull();
        assertThat(professional.getSubscriptionExpiresAt()).isNull();
        verify(professionalRepository).save(professional);
    }

    @Test
    void cancelShouldReturnCurrentBenefitsWindow() {
        UUID professionalId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant expiration = Instant.now().plus(20, ChronoUnit.DAYS);

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);
        professional.setSubscriptionPlanId(planId);
        professional.setSubscriptionExpiresAt(expiration);

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Plano Pro")
                .priceMonthly(new BigDecimal("49.90"))
                .active(true)
                .build();
        plan.setId(planId);

        CancelSubscriptionResponse response = new CancelSubscriptionResponse(
                professionalId,
                planId,
                "Plano Pro",
                expiration,
                Instant.now(),
                "Assinatura cancelada. Os beneficios permanecem ate o fim do periodo vigente."
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(planId)).thenReturn(Optional.of(plan));
        when(professionalSubscriptionMapper.toCancelResponse(professional, plan)).thenReturn(response);

        CancelSubscriptionResponse result = professionalSubscriptionService.cancel(professionalId);

        assertThat(result).isEqualTo(response);
        assertThat(result.benefitsUntil()).isEqualTo(expiration);
    }

    @Test
    void assignPlanShouldClearExpiredSubscriptionBeforeCreatingNewOne() {
        UUID professionalId = UUID.randomUUID();
        UUID oldPlanId = UUID.randomUUID();
        UUID newPlanId = UUID.randomUUID();

        Professional professional = Professional.builder().userId(UUID.randomUUID()).build();
        professional.setId(professionalId);
        professional.setSubscriptionPlanId(oldPlanId);
        professional.setSubscriptionExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        SubscriptionPlan newPlan = SubscriptionPlan.builder()
                .name("Plano Premium")
                .priceMonthly(new BigDecimal("79.90"))
                .active(true)
                .build();
        newPlan.setId(newPlanId);

        ProfessionalSubscriptionResponse response = new ProfessionalSubscriptionResponse(
                professionalId,
                newPlanId,
                "Plano Premium",
                new BigDecimal("79.90"),
                false,
                false,
                null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                true,
                null
        );

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.of(professional));
        when(subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(newPlanId)).thenReturn(Optional.of(newPlan));
        when(professionalRepository.save(professional)).thenReturn(professional);
        when(professionalSubscriptionMapper.toResponse(professional, newPlan)).thenReturn(response);

        ProfessionalSubscriptionResponse result = professionalSubscriptionService.assignPlan(
                professionalId,
                new AssignSubscriptionPlanRequest(newPlanId)
        );

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Professional> captor = ArgumentCaptor.forClass(Professional.class);
        verify(professionalRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getSubscriptionPlanId()).isEqualTo(newPlanId);
    }

    @Test
    void findCurrentShouldRejectWhenProfessionalDoesNotExist() {
        UUID professionalId = UUID.randomUUID();

        when(professionalRepository.findByIdAndDeletedAtIsNull(professionalId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> professionalSubscriptionService.findCurrent(professionalId))
                .isInstanceOf(ProfessionalNotFoundException.class)
                .hasMessageContaining(professionalId.toString());
    }
}
