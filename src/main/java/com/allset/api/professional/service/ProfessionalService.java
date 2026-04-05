package com.allset.api.professional.service;

import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProfessionalService {

    ProfessionalResponse create(CreateProfessionalRequest request);

    ProfessionalResponse findById(UUID id);

    ProfessionalResponse findByUserId(UUID userId);

    Page<ProfessionalResponse> findAll(VerificationStatus status, boolean geoActive, Pageable pageable);

    ProfessionalResponse update(UUID id, UpdateProfessionalRequest request);

    ProfessionalResponse updateGeo(UUID id, UpdateGeoRequest request);

    ProfessionalResponse verify(UUID id, VerifyProfessionalRequest request);

    void delete(UUID id);
}
