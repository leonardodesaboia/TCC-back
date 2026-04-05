package com.allset.api.subscription.service;

import com.allset.api.subscription.dto.CreateSubscriptionPlanRequest;
import com.allset.api.subscription.dto.SubscriptionPlanResponse;
import com.allset.api.subscription.dto.UpdateSubscriptionPlanRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SubscriptionPlanService {

    SubscriptionPlanResponse create(CreateSubscriptionPlanRequest request);

    Page<SubscriptionPlanResponse> findAll(boolean includeInactive, Pageable pageable);

    SubscriptionPlanResponse findById(UUID id);

    SubscriptionPlanResponse update(UUID id, UpdateSubscriptionPlanRequest request);

    void delete(UUID id);
}
