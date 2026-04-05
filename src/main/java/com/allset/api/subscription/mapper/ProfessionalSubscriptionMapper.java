package com.allset.api.subscription.mapper;

import com.allset.api.professional.domain.Professional;
import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.dto.CancelSubscriptionResponse;
import com.allset.api.subscription.dto.ProfessionalSubscriptionResponse;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalSubscriptionMapper {

    public ProfessionalSubscriptionResponse toResponse(Professional professional, SubscriptionPlan plan) {
        return new ProfessionalSubscriptionResponse(
                professional.getId(),
                plan.getId(),
                plan.getName(),
                plan.getPriceMonthly(),
                plan.isHighlightInSearch(),
                plan.isExpressPriority(),
                plan.getBadgeLabel(),
                professional.getSubscriptionExpiresAt()
        );
    }

    public CancelSubscriptionResponse toCancelResponse(Professional professional, SubscriptionPlan plan) {
        return new CancelSubscriptionResponse(
                professional.getId(),
                plan.getId(),
                plan.getName(),
                professional.getSubscriptionExpiresAt(),
                "Assinatura cancelada. Os beneficios permanecem ate o fim do periodo vigente."
        );
    }
}
