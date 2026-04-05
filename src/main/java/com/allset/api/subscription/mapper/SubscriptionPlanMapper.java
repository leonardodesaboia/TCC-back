package com.allset.api.subscription.mapper;

import com.allset.api.subscription.domain.SubscriptionPlan;
import com.allset.api.subscription.dto.SubscriptionPlanResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubscriptionPlanMapper {

    public SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getPriceMonthly(),
                plan.isHighlightInSearch(),
                plan.isExpressPriority(),
                plan.getBadgeLabel(),
                plan.isActive(),
                plan.getCreatedAt()
        );
    }

    public List<SubscriptionPlanResponse> toResponseList(List<SubscriptionPlan> plans) {
        return plans.stream().map(this::toResponse).toList();
    }
}
