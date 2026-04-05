package com.allset.api.subscription.service;

import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.dto.CreateSubscriptionPlanRequest;
import com.allset.api.subscription.dto.SubscriptionPlanResponse;
import com.allset.api.subscription.dto.UpdateSubscriptionPlanRequest;
import com.allset.api.subscription.exception.SubscriptionPlanNameAlreadyExistsException;
import com.allset.api.subscription.exception.SubscriptionPlanNotFoundException;
import com.allset.api.subscription.mapper.SubscriptionPlanMapper;
import com.allset.api.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPlanMapper subscriptionPlanMapper;

    @Override
    public SubscriptionPlanResponse create(CreateSubscriptionPlanRequest request) {
        if (subscriptionPlanRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.name())) {
            throw new SubscriptionPlanNameAlreadyExistsException(request.name());
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(request.name())
                .priceMonthly(request.priceMonthly())
                .highlightInSearch(Boolean.TRUE.equals(request.highlightInSearch()))
                .expressPriority(Boolean.TRUE.equals(request.expressPriority()))
                .badgeLabel(request.badgeLabel())
                .active(request.active() == null || request.active())
                .build();

        return subscriptionPlanMapper.toResponse(subscriptionPlanRepository.save(plan));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SubscriptionPlanResponse> findAll(boolean includeInactive, Pageable pageable) {
        if (includeInactive) {
            return subscriptionPlanRepository.findAllByDeletedAtIsNull(pageable).map(subscriptionPlanMapper::toResponse);
        }

        return subscriptionPlanRepository.findAllByActiveTrueAndDeletedAtIsNull(pageable).map(subscriptionPlanMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanResponse findById(UUID id) {
        return subscriptionPlanMapper.toResponse(findActiveById(id));
    }

    @Override
    public SubscriptionPlanResponse update(UUID id, UpdateSubscriptionPlanRequest request) {
        SubscriptionPlan plan = findActiveById(id);

        if (request.name() != null && !request.name().equalsIgnoreCase(plan.getName())) {
            if (subscriptionPlanRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.name())) {
                throw new SubscriptionPlanNameAlreadyExistsException(request.name());
            }
            plan.setName(request.name());
        }

        if (request.priceMonthly() != null) {
            plan.setPriceMonthly(request.priceMonthly());
        }

        if (request.highlightInSearch() != null) {
            plan.setHighlightInSearch(request.highlightInSearch());
        }

        if (request.expressPriority() != null) {
            plan.setExpressPriority(request.expressPriority());
        }

        if (request.badgeLabel() != null) {
            plan.setBadgeLabel(request.badgeLabel());
        }

        if (request.active() != null) {
            plan.setActive(request.active());
        }

        return subscriptionPlanMapper.toResponse(subscriptionPlanRepository.save(plan));
    }

    @Override
    public void delete(UUID id) {
        SubscriptionPlan plan = findActiveById(id);
        plan.setDeletedAt(Instant.now());
        subscriptionPlanRepository.save(plan);
    }

    private SubscriptionPlan findActiveById(UUID id) {
        return subscriptionPlanRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new SubscriptionPlanNotFoundException(id));
    }
}
