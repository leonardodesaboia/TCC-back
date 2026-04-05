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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfessionalSubscriptionServiceImpl implements ProfessionalSubscriptionService {

    private final ProfessionalRepository professionalRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ProfessionalSubscriptionMapper professionalSubscriptionMapper;

    @Override
    public ProfessionalSubscriptionResponse findCurrent(UUID professionalId) {
        Professional professional = findActiveProfessional(professionalId);
        clearExpiredSubscription(professional);

        if (!hasActiveSubscription(professional)) {
            throw new ProfessionalSubscriptionNotFoundException(professionalId);
        }

        SubscriptionPlan plan = findActivePlan(professional.getSubscriptionPlanId());
        return professionalSubscriptionMapper.toResponse(professional, plan);
    }

    @Override
    public ProfessionalSubscriptionResponse assignPlan(UUID professionalId, AssignSubscriptionPlanRequest request) {
        Professional professional = findActiveProfessional(professionalId);
        clearExpiredSubscription(professional);

        SubscriptionPlan plan = findActivePlan(request.subscriptionPlanId());

        if (request.subscriptionPlanId().equals(professional.getSubscriptionPlanId()) && hasActiveSubscription(professional)) {
            throw new SubscriptionPlanAlreadyActiveException(professionalId, request.subscriptionPlanId());
        }

        if (hasActiveSubscription(professional)) {
            professional.setSubscriptionPlanId(plan.getId());
        } else {
            professional.setSubscriptionPlanId(plan.getId());
            professional.setSubscriptionExpiresAt(nextMonthlyExpiration());
        }

        Professional savedProfessional = professionalRepository.save(professional);
        return professionalSubscriptionMapper.toResponse(savedProfessional, plan);
    }

    @Override
    public CancelSubscriptionResponse cancel(UUID professionalId) {
        Professional professional = findActiveProfessional(professionalId);
        clearExpiredSubscription(professional);

        if (!hasActiveSubscription(professional)) {
            throw new ProfessionalSubscriptionNotFoundException(professionalId);
        }

        SubscriptionPlan plan = findActivePlan(professional.getSubscriptionPlanId());
        return professionalSubscriptionMapper.toCancelResponse(professional, plan);
    }

    private Professional findActiveProfessional(UUID professionalId) {
        return professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));
    }

    private SubscriptionPlan findActivePlan(UUID planId) {
        return subscriptionPlanRepository.findByIdAndActiveTrueAndDeletedAtIsNull(planId)
                .orElseThrow(() -> new SubscriptionPlanNotFoundException(planId));
    }

    private boolean hasActiveSubscription(Professional professional) {
        return professional.getSubscriptionPlanId() != null
                && professional.getSubscriptionExpiresAt() != null
                && professional.getSubscriptionExpiresAt().isAfter(Instant.now());
    }

    private void clearExpiredSubscription(Professional professional) {
        if (professional.getSubscriptionPlanId() == null || professional.getSubscriptionExpiresAt() == null) {
            return;
        }

        if (!professional.getSubscriptionExpiresAt().isAfter(Instant.now())) {
            professional.setSubscriptionPlanId(null);
            professional.setSubscriptionExpiresAt(null);
            professionalRepository.save(professional);
        }
    }

    private Instant nextMonthlyExpiration() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1).toInstant();
    }
}
