package com.allset.api.offering.service;

import com.allset.api.catalog.exception.ServiceCategoryNotFoundException;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.dto.CreateProfessionalOfferingRequest;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import com.allset.api.offering.dto.UpdateProfessionalOfferingRequest;
import com.allset.api.offering.exception.ProfessionalOfferingNotFoundException;
import com.allset.api.offering.mapper.ProfessionalOfferingMapper;
import com.allset.api.offering.repository.ProfessionalOfferingRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.domain.VerificationStatus;
import com.allset.api.professional.exception.ProfessionalNotFoundException;
import com.allset.api.professional.exception.ProfessionalNotApprovedException;
import com.allset.api.professional.repository.ProfessionalRepository;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
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
public class ProfessionalOfferingServiceImpl implements ProfessionalOfferingService {

    private final ProfessionalOfferingRepository professionalOfferingRepository;
    private final ProfessionalRepository professionalRepository;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ProfessionalOfferingMapper professionalOfferingMapper;

    @Override
    public ProfessionalOfferingResponse create(UUID professionalId, CreateProfessionalOfferingRequest request) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));
        requireApproved(professional);

        serviceCategoryRepository.findByIdAndDeletedAtIsNull(request.categoryId())
                .orElseThrow(() -> new ServiceCategoryNotFoundException(request.categoryId()));

        requireSpecialty(professionalId, request.categoryId());
        validatePrice(professionalId, request.categoryId(), request.pricingType(), request.price());

        ProfessionalOffering offering = ProfessionalOffering.builder()
                .professionalId(professionalId)
                .categoryId(request.categoryId())
                .title(request.title())
                .description(request.description())
                .pricingType(request.pricingType())
                .price(request.price())
                .estimatedDurationMinutes(request.estimatedDurationMinutes())
                .build();

        return professionalOfferingMapper.toResponse(professionalOfferingRepository.save(offering));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfessionalOfferingResponse> findAllByProfessional(UUID professionalId, boolean includeInactive, Pageable pageable) {
        professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));

        if (includeInactive) {
            return professionalOfferingRepository
                    .findAllByProfessionalIdAndDeletedAtIsNull(professionalId, pageable)
                    .map(professionalOfferingMapper::toResponse);
        }
        return professionalOfferingRepository
                .findAllByProfessionalIdAndActiveTrueAndDeletedAtIsNull(professionalId, pageable)
                .map(professionalOfferingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalOfferingResponse findById(UUID professionalId, UUID id) {
        return professionalOfferingMapper.toResponse(findOwned(professionalId, id));
    }

    @Override
    public ProfessionalOfferingResponse update(UUID professionalId, UUID id, UpdateProfessionalOfferingRequest request) {
        Professional professional = professionalRepository.findByIdAndDeletedAtIsNull(professionalId)
                .orElseThrow(() -> new ProfessionalNotFoundException(professionalId));
        requireApproved(professional);

        ProfessionalOffering offering = findOwned(professionalId, id);

        if (request.title() != null) offering.setTitle(request.title());
        if (request.description() != null) offering.setDescription(request.description());
        if (request.pricingType() != null) offering.setPricingType(request.pricingType());
        if (request.price() != null) offering.setPrice(request.price());
        if (request.estimatedDurationMinutes() != null) offering.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        if (request.active() != null) offering.setActive(request.active());

        if (Boolean.TRUE.equals(request.clearPrice())) {
            offering.setPrice(null);
        }

        validatePrice(professionalId, offering.getCategoryId(), offering.getPricingType(), offering.getPrice());

        return professionalOfferingMapper.toResponse(professionalOfferingRepository.save(offering));
    }

    @Override
    public void delete(UUID professionalId, UUID id) {
        ProfessionalOffering offering = findOwned(professionalId, id);
        offering.setDeletedAt(Instant.now());
        professionalOfferingRepository.save(offering);
    }

    private void requireApproved(Professional professional) {
        if (professional.getVerificationStatus() != VerificationStatus.approved) {
            throw new ProfessionalNotApprovedException(professional.getVerificationStatus());
        }
    }

    private void validatePrice(UUID professionalId, UUID categoryId, PricingType pricingType, java.math.BigDecimal price) {
        if (pricingType == PricingType.fixed && price == null) {
            throw new IllegalArgumentException("Preço é obrigatório para serviços com precificação fixa (fixed)");
        }
        if (pricingType == PricingType.hourly && price == null) {
            boolean hasSpecialtyRate = professionalSpecialtyRepository
                    .findByProfessionalIdAndCategoryIdAndDeletedAtIsNull(professionalId, categoryId)
                    .map(s -> s.getHourlyRate() != null)
                    .orElse(false);
            if (!hasSpecialtyRate) {
                throw new IllegalArgumentException(
                        "Preço é obrigatório: a especialidade vinculada não possui valor/hora definido");
            }
        }
    }

    private void requireSpecialty(UUID professionalId, UUID categoryId) {
        professionalSpecialtyRepository.findByProfessionalIdAndCategoryIdAndDeletedAtIsNull(professionalId, categoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "O serviço deve estar vinculado a uma das profissões cadastradas pelo profissional. Categoria não encontrada nas especialidades: " + categoryId));
    }

    private ProfessionalOffering findOwned(UUID professionalId, UUID id) {
        return professionalOfferingRepository.findByIdAndProfessionalIdAndDeletedAtIsNull(id, professionalId)
                .orElseThrow(() -> new ProfessionalOfferingNotFoundException(id));
    }
}
