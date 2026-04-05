package com.allset.api.catalog.repository;

import com.allset.api.catalog.domain.ServiceArea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceAreaRepository extends JpaRepository<ServiceArea, UUID> {

    Optional<ServiceArea> findByIdAndDeletedAtIsNull(UUID id);

    Page<ServiceArea> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ServiceArea> findAllByActiveTrueAndDeletedAtIsNull(Pageable pageable);

    boolean existsByNameAndDeletedAtIsNull(String name);
}
