package com.allset.api.document.repository;

import com.allset.api.document.domain.ProfessionalDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfessionalDocumentRepository extends JpaRepository<ProfessionalDocument, UUID> {

    List<ProfessionalDocument> findAllByProfessionalId(UUID professionalId);

    Optional<ProfessionalDocument> findByIdAndProfessionalId(UUID id, UUID professionalId);
}
