package com.allset.api.professional.service;

import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.*;
import com.allset.api.professional.exception.ProfessionalAlreadyExistsException;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.mapper.ProfessionalMapper;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.user.repository.UserRepository;
import com.allset.api.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProfessionalServiceImpl implements ProfessionalService {

    private final ProfessionalRepository professionalRepository;
    private final UserRepository userRepository;
    private final ProfessionalMapper professionalMapper;

    @Override
    public ProfessionalResponse create(CreateProfessionalRequest request) {
        userRepository.findByIdAndDeletedAtIsNull(request.userId())
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        if (professionalRepository.existsByUserIdAndDeletedAtIsNull(request.userId())) {
            throw new ProfessionalAlreadyExistsException(request.userId());
        }

        Professional professional = Professional.builder()
                .userId(request.userId())
                .bio(request.bio())
                .yearsOfExperience(request.yearsOfExperience())
                .baseHourlyRate(request.baseHourlyRate())
                .build();

        return professionalMapper.toResponse(professionalRepository.save(professional));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalResponse findById(UUID id) {
        return professionalMapper.toResponse(findActiveById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalResponse findByUserId(UUID userId) {
        Professional professional = professionalRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ProfessionalNotFoundException(userId));
        return professionalMapper.toResponse(professional);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalResponse> findAll(VerificationStatus status, boolean geoActive, Pageable pageable) {
        Page<Professional> page;
        if (geoActive && status != null) {
            page = professionalRepository.findAllByGeoActiveTrueAndVerificationStatusAndDeletedAtIsNull(status, pageable);
        } else if (geoActive) {
            page = professionalRepository.findAllByGeoActiveTrueAndDeletedAtIsNull(pageable);
        } else if (status != null) {
            page = professionalRepository.findAllByVerificationStatusAndDeletedAtIsNull(status, pageable);
        } else {
            page = professionalRepository.findAllByDeletedAtIsNull(pageable);
        }
        return page.map(professionalMapper::toResponse);
    }

    @Override
    public ProfessionalResponse update(UUID id, UpdateProfessionalRequest request) {
        Professional professional = findActiveById(id);

        if (request.bio() != null) professional.setBio(request.bio());
        if (request.yearsOfExperience() != null) professional.setYearsOfExperience(request.yearsOfExperience());
        if (request.baseHourlyRate() != null) professional.setBaseHourlyRate(request.baseHourlyRate());

        return professionalMapper.toResponse(professionalRepository.save(professional));
    }

    @Override
    public ProfessionalResponse updateGeo(UUID id, UpdateGeoRequest request) {
        Professional professional = findActiveById(id);

        if (Boolean.TRUE.equals(request.geoActive())) {
            boolean hasLat = request.geoLat() != null || professional.getGeoLat() != null;
            boolean hasLng = request.geoLng() != null || professional.getGeoLng() != null;
            if (!hasLat || !hasLng) {
                throw new IllegalArgumentException(
                        "Coordenadas geográficas (geoLat e geoLng) são obrigatórias para ativar o modo Express");
            }
        }

        professional.setGeoActive(request.geoActive());
        if (request.geoLat() != null) professional.setGeoLat(request.geoLat());
        if (request.geoLng() != null) professional.setGeoLng(request.geoLng());

        return professionalMapper.toResponse(professionalRepository.save(professional));
    }

    @Override
    public ProfessionalResponse verify(UUID id, VerifyProfessionalRequest request) {
        Professional professional = findActiveById(id);

        if (request.status() == VerificationStatus.rejected && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Motivo de rejeição é obrigatório quando status = rejected");
        }

        professional.setVerificationStatus(request.status());
        professional.setRejectionReason(request.status() == VerificationStatus.rejected ? request.rejectionReason() : null);

        return professionalMapper.toResponse(professionalRepository.save(professional));
    }

    @Override
    public void delete(UUID id) {
        Professional professional = findActiveById(id);
        professional.setDeletedAt(Instant.now());
        professionalRepository.save(professional);
    }

    private Professional findActiveById(UUID id) {
        return professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ProfessionalNotFoundException(id));
    }
}
