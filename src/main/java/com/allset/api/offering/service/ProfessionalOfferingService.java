package com.allset.api.offering.service;

import com.allset.api.offering.dto.CreateProfessionalOfferingRequest;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import com.allset.api.offering.dto.UpdateProfessionalOfferingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProfessionalOfferingService {

    ProfessionalOfferingResponse create(UUID professionalId, CreateProfessionalOfferingRequest request);

    Page<ProfessionalOfferingResponse> findAllByProfessional(UUID professionalId, boolean includeInactive, Pageable pageable);

    ProfessionalOfferingResponse findById(UUID professionalId, UUID id);

    ProfessionalOfferingResponse update(UUID professionalId, UUID id, UpdateProfessionalOfferingRequest request);

    void delete(UUID professionalId, UUID id);
}
