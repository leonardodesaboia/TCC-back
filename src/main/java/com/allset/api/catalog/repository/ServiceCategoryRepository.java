package com.allset.api.catalog.repository;

import com.allset.api.catalog.domain.ServiceCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, UUID> {

    Optional<ServiceCategory> findByIdAndDeletedAtIsNull(UUID id);

    Page<ServiceCategory> findAllByDeletedAtIsNull(Pageable pageable);

    Page<ServiceCategory> findAllByAreaIdAndDeletedAtIsNull(UUID areaId, Pageable pageable);

    Page<ServiceCategory> findAllByAreaIdAndActiveTrueAndDeletedAtIsNull(UUID areaId, Pageable pageable);

    Page<ServiceCategory> findAllByActiveTrueAndDeletedAtIsNull(Pageable pageable);
}
