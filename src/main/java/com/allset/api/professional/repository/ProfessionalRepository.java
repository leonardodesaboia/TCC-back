package com.allset.api.professional.repository;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {

    Optional<Professional> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Professional> findByUserIdAndDeletedAtIsNull(UUID userId);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);

    Page<Professional> findAllByDeletedAtIsNull(Pageable pageable);

    Page<Professional> findAllByVerificationStatusAndDeletedAtIsNull(VerificationStatus status, Pageable pageable);

    Page<Professional> findAllByGeoActiveTrueAndDeletedAtIsNull(Pageable pageable);

    Page<Professional> findAllByGeoActiveTrueAndVerificationStatusAndDeletedAtIsNull(
            VerificationStatus status, Pageable pageable);

    List<Professional> findAllBySubscriptionPlanIdIsNotNullAndSubscriptionExpiresAtLessThanEqualAndDeletedAtIsNull(Instant expiresAt);
}
