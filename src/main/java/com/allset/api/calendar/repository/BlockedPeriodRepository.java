package com.allset.api.calendar.repository;

import com.allset.api.calendar.domain.BlockedPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlockedPeriodRepository extends JpaRepository<BlockedPeriod, UUID> {

    List<BlockedPeriod> findAllByProfessionalId(UUID professionalId);

    Optional<BlockedPeriod> findByIdAndProfessionalId(UUID id, UUID professionalId);
}
