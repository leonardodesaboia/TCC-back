package com.allset.api.subscription.repository;

import com.allset.api.subscription.domain.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByIdAndDeletedAtIsNull(UUID id);

    Page<SubscriptionPlan> findAllByDeletedAtIsNull(Pageable pageable);

    Page<SubscriptionPlan> findAllByActiveTrueAndDeletedAtIsNull(Pageable pageable);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);
}
