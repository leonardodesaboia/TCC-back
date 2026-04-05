package com.allset.api.offering.repository;

import com.allset.api.offering.domain.ProfessionalOffering;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalOfferingRepository extends JpaRepository<ProfessionalOffering, UUID> {

    Page<ProfessionalOffering> findAllByProfessionalIdAndDeletedAtIsNull(UUID professionalId, Pageable pageable);

    Page<ProfessionalOffering> findAllByProfessionalIdAndActiveTrueAndDeletedAtIsNull(UUID professionalId, Pageable pageable);

    Optional<ProfessionalOffering> findByIdAndProfessionalIdAndDeletedAtIsNull(UUID id, UUID professionalId);
}
