package com.allset.api.subscription.service;

import com.allset.api.subscription.dto.AssignSubscriptionPlanRequest;
import com.allset.api.subscription.dto.CancelSubscriptionResponse;
import com.allset.api.subscription.dto.ProfessionalSubscriptionResponse;

import java.util.UUID;

public interface ProfessionalSubscriptionService {

    ProfessionalSubscriptionResponse findCurrent(UUID professionalId);

    ProfessionalSubscriptionResponse assignPlan(UUID professionalId, AssignSubscriptionPlanRequest request);

    CancelSubscriptionResponse cancel(UUID professionalId);
}
