package com.allset.api.subscription.service;

import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.dto.CreateSubscriptionPlanRequest;
import com.allset.api.subscription.dto.SubscriptionPlanResponse;
import com.allset.api.subscription.dto.UpdateSubscriptionPlanRequest;
import com.allset.api.subscription.exception.SubscriptionPlanNameAlreadyExistsException;
import com.allset.api.subscription.exception.SubscriptionPlanNotFoundException;
import com.allset.api.subscription.mapper.SubscriptionPlanMapper;
import com.allset.api.subscription.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServiceImplTest {

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private SubscriptionPlanMapper subscriptionPlanMapper;

    @InjectMocks
    private SubscriptionPlanServiceImpl subscriptionPlanService;

    @Test
    void createShouldRejectDuplicatedActiveNameIgnoringCase() {
        CreateSubscriptionPlanRequest request = new CreateSubscriptionPlanRequest(
                "Plano Pro",
                new BigDecimal("49.90"),
                true,
                true,
                "Pro",
                true
        );

        when(subscriptionPlanRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Plano Pro")).thenReturn(true);

        assertThatThrownBy(() -> subscriptionPlanService.create(request))
                .isInstanceOf(SubscriptionPlanNameAlreadyExistsException.class)
                .hasMessageContaining("Plano Pro");
    }

    @Test
    void updateShouldPersistChangedFields() {
        UUID id = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Plano Start")
                .priceMonthly(new BigDecimal("19.90"))
                .highlightInSearch(false)
                .expressPriority(false)
                .badgeLabel("Start")
                .active(true)
                .build();
        plan.setId(id);
        plan.setCreatedAt(Instant.now());

        UpdateSubscriptionPlanRequest request = new UpdateSubscriptionPlanRequest(
                "Plano Premium",
                new BigDecimal("59.90"),
                true,
                true,
                "Premium",
                false
        );

        SubscriptionPlanResponse response = new SubscriptionPlanResponse(
                id,
                "Plano Premium",
                new BigDecimal("59.90"),
                true,
                true,
                "Premium",
                false,
                plan.getCreatedAt()
        );

        when(subscriptionPlanRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(plan));
        when(subscriptionPlanRepository.existsByNameIgnoreCaseAndDeletedAtIsNull("Plano Premium")).thenReturn(false);
        when(subscriptionPlanRepository.save(plan)).thenReturn(plan);
        when(subscriptionPlanMapper.toResponse(plan)).thenReturn(response);

        SubscriptionPlanResponse result = subscriptionPlanService.update(id, request);

        assertThat(result).isEqualTo(response);
        assertThat(plan.getName()).isEqualTo("Plano Premium");
        assertThat(plan.getPriceMonthly()).isEqualByComparingTo("59.90");
        assertThat(plan.isHighlightInSearch()).isTrue();
        assertThat(plan.isExpressPriority()).isTrue();
        assertThat(plan.getBadgeLabel()).isEqualTo("Premium");
        assertThat(plan.isActive()).isFalse();
    }

    @Test
    void deleteShouldSoftDeletePlan() {
        UUID id = UUID.randomUUID();
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name("Plano Pro")
                .priceMonthly(new BigDecimal("49.90"))
                .build();
        plan.setId(id);

        when(subscriptionPlanRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(plan));

        subscriptionPlanService.delete(id);

        ArgumentCaptor<SubscriptionPlan> captor = ArgumentCaptor.forClass(SubscriptionPlan.class);
        verify(subscriptionPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void findByIdShouldFailWhenPlanIsSoftDeletedOrMissing() {
        UUID id = UUID.randomUUID();

        when(subscriptionPlanRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionPlanService.findById(id))
                .isInstanceOf(SubscriptionPlanNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
