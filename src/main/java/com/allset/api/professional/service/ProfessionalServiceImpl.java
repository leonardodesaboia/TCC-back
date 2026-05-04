package com.allset.api.professional.service;

import com.allset.api.catalog.exception.ServiceCategoryNotFoundException;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.document.repository.ProfessionalDocumentRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.ProfessionalSpecialty;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.dto.*;
import com.allset.api.professional.exception.ProfessionalAlreadyExistsException;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.mapper.ProfessionalMapper;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
import com.allset.api.user.exception.UserNotFoundException;
import com.allset.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.allset.api.professional.exception.ProfessionalNotApprovedException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProfessionalServiceImpl implements ProfessionalService {

    /** Tolerância de relógio adiantado: capturedAt até este valor no futuro é aceito tal qual. */
    static final Duration GEO_FUTURE_SKEW_TOLERANCE = Duration.ofSeconds(30);
    /** Tolerância de relógio atrasado: capturedAt até este valor no passado é aceito tal qual. */
    static final Duration GEO_PAST_SKEW_TOLERANCE = Duration.ofHours(1);

    private final ProfessionalRepository professionalRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;
    private final ProfessionalDocumentRepository professionalDocumentRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
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
                .yearsOfExperience(resolveYearsOfExperience(request.yearsOfExperience(), request.specialties()))
                .baseHourlyRate(request.baseHourlyRate())
                .build();

        Professional saved = professionalRepository.save(professional);
        replaceSpecialties(saved.getId(), request.specialties());
        return professionalMapper.toResponse(saved);
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
        requireApproved(professional);

        if (request.bio() != null) professional.setBio(request.bio());
        if (request.baseHourlyRate() != null) professional.setBaseHourlyRate(request.baseHourlyRate());
        if (request.specialties() != null) {
            replaceSpecialties(professional.getId(), request.specialties());
        }
        if (request.yearsOfExperience() != null || request.specialties() != null) {
            professional.setYearsOfExperience(resolveYearsOfExperience(request.yearsOfExperience(), request.specialties()));
        }

        return professionalMapper.toResponse(professionalRepository.save(professional));
    }

    @Override
    public ProfessionalResponse updateGeo(UUID id, UpdateGeoRequest request) {
        Professional professional = findActiveById(id);
        requireApproved(professional);

        if (Boolean.TRUE.equals(request.geoActive())) {
            boolean hasLat = request.geoLat() != null || professional.getGeoLat() != null;
            boolean hasLng = request.geoLng() != null || professional.getGeoLng() != null;
            if (!hasLat || !hasLng) {
                throw new IllegalArgumentException(
                        "Coordenadas geográficas (geoLat e geoLng) são obrigatórias para ativar o modo Express");
            }
        }

        boolean previouslyActive = professional.isGeoActive();
        professional.setGeoActive(request.geoActive());
        if (request.geoLat() != null) professional.setGeoLat(request.geoLat());
        if (request.geoLng() != null) professional.setGeoLng(request.geoLng());

        if (Boolean.TRUE.equals(request.geoActive())) {
            professional.setGeoCapturedAt(resolveCapturedAt(request.capturedAt(), id));
            if (request.accuracyMeters() != null) professional.setGeoAccuracyMeters(request.accuracyMeters());
            if (request.source() != null) professional.setGeoSource(request.source());
        } else {
            professional.setGeoCapturedAt(null);
            professional.setGeoAccuracyMeters(null);
            professional.setGeoSource(null);
        }

        if (previouslyActive != professional.isGeoActive()) {
            log.info("event=professional_geo_active_changed professionalId={} active={}", id, professional.isGeoActive());
        }

        return professionalMapper.toResponse(professionalRepository.save(professional));
    }

    private Instant resolveCapturedAt(Instant requested, UUID id) {
        Instant now = Instant.now();
        if (requested == null) return now;
        Instant futureLimit = now.plus(GEO_FUTURE_SKEW_TOLERANCE);
        Instant pastLimit   = now.minus(GEO_PAST_SKEW_TOLERANCE);
        if (requested.isAfter(futureLimit) || requested.isBefore(pastLimit)) {
            log.warn("event=professional_geo_clock_skew professionalId={} requested={} now={}", id, requested, now);
            return now;
        }
        return requested;
    }

    @Override
    public ProfessionalResponse verify(UUID id, VerifyProfessionalRequest request) {
        Professional professional = findActiveById(id);

        if (request.status() == VerificationStatus.rejected && (request.rejectionReason() == null || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Motivo de rejeição é obrigatório quando status = rejected");
        }

        if (request.status() == VerificationStatus.approved) {
            long docCount = professionalDocumentRepository.countByProfessionalId(id);
            if (docCount < 2) {
                throw new IllegalArgumentException(
                        "Não é possível aprovar: profissional precisa ter ao menos frente e verso do documento enviados");
            }

            List<ProfessionalSpecialty> specialties = professionalSpecialtyRepository
                    .findAllByProfessionalIdAndDeletedAtIsNullOrderByCreatedAtAsc(id);
            if (specialties.isEmpty()) {
                throw new IllegalArgumentException(
                        "Não é possível aprovar: profissional precisa ter ao menos uma especialidade cadastrada");
            }
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

    private void requireApproved(Professional professional) {
        if (professional.getVerificationStatus() != VerificationStatus.approved) {
            throw new ProfessionalNotApprovedException(professional.getVerificationStatus());
        }
    }

    private Professional findActiveById(UUID id) {
        return professionalRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ProfessionalNotFoundException(id));
    }

    private void replaceSpecialties(UUID professionalId, List<ProfessionalSpecialtyRequest> requests) {
        validateSpecialties(requests);
        ensureCategoriesExist(requests);

        Map<UUID, ProfessionalSpecialty> existingByCategory = professionalSpecialtyRepository
                .findAllByProfessionalIdAndDeletedAtIsNullOrderByCreatedAtAsc(professionalId)
                .stream()
                .collect(Collectors.toMap(ProfessionalSpecialty::getCategoryId, Function.identity()));

        HashSet<UUID> requestedCategoryIds = new HashSet<>();
        for (ProfessionalSpecialtyRequest request : requests) {
            requestedCategoryIds.add(request.categoryId());
            ProfessionalSpecialty specialty = existingByCategory.get(request.categoryId());
            if (specialty == null) {
                specialty = ProfessionalSpecialty.builder()
                        .professionalId(professionalId)
                        .categoryId(request.categoryId())
                        .yearsOfExperience(request.yearsOfExperience())
                        .hourlyRate(request.hourlyRate())
                        .build();
            } else {
                specialty.setYearsOfExperience(request.yearsOfExperience());
                specialty.setHourlyRate(request.hourlyRate());
            }
            professionalSpecialtyRepository.save(specialty);
        }

        List<ProfessionalSpecialty> toDelete = existingByCategory.values().stream()
                .filter(existing -> !requestedCategoryIds.contains(existing.getCategoryId()))
                .toList();
        if (!toDelete.isEmpty()) {
            professionalSpecialtyRepository.deleteAll(toDelete);
        }
    }

    private void validateSpecialties(List<ProfessionalSpecialtyRequest> requests) {
        HashSet<UUID> categoryIds = new HashSet<>();
        for (ProfessionalSpecialtyRequest request : requests) {
            if (!categoryIds.add(request.categoryId())) {
                throw new IllegalArgumentException("Categoria profissional duplicada no cadastro: " + request.categoryId());
            }
        }
    }

    private void ensureCategoriesExist(List<ProfessionalSpecialtyRequest> requests) {
        for (ProfessionalSpecialtyRequest request : requests) {
            serviceCategoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                    .orElseThrow(() -> new ServiceCategoryNotFoundException(request.categoryId()));
        }
    }

    private Short resolveYearsOfExperience(Short explicitYears, List<ProfessionalSpecialtyRequest> specialties) {
        if (explicitYears != null) {
            return explicitYears;
        }
        if (specialties == null || specialties.isEmpty()) {
            return null;
        }

        int maxYears = specialties.stream()
                .map(ProfessionalSpecialtyRequest::yearsOfExperience)
                .mapToInt(Short::intValue)
                .max()
                .orElse(0);
        return (short) maxYears;
    }
}
