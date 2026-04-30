package com.allset.api.professional.repository;

import com.allset.api.professional.domain.ProfessionalSpecialty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfessionalSpecialtyRepository extends JpaRepository<ProfessionalSpecialty, UUID> {

    List<ProfessionalSpecialty> findAllByProfessionalIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID professionalId);

    List<ProfessionalSpecialty> findAllByProfessionalIdInAndDeletedAtIsNullOrderByCreatedAtAsc(List<UUID> professionalIds);

    List<ProfessionalSpecialty> findAllByCategoryIdInAndDeletedAtIsNull(List<UUID> categoryIds);

    Optional<ProfessionalSpecialty> findByProfessionalIdAndCategoryIdAndDeletedAtIsNull(UUID professionalId, UUID categoryId);
}
