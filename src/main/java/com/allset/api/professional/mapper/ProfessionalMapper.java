package com.allset.api.professional.mapper;

import com.allset.api.catalog.domain.ServiceArea;
import com.allset.api.catalog.domain.ServiceCategory;
import com.allset.api.catalog.repository.ServiceAreaRepository;
import com.allset.api.catalog.repository.ServiceCategoryRepository;
import com.allset.api.professional.domain.Professional;
import com.allset.api.professional.dto.ProfessionalResponse;
import com.allset.api.professional.dto.ProfessionalSpecialtyResponse;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.service.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProfessionalMapper {

    private final ReviewSummaryService reviewSummaryService;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final ServiceAreaRepository serviceAreaRepository;

    public ProfessionalResponse toResponse(Professional professional) {
        ReviewRatingSummary ratingSummary = reviewSummaryService.summarizeProfessional(professional.getId());
        List<ProfessionalSpecialtyResponse> specialties = mapSpecialties(professional.getId());

        return new ProfessionalResponse(
                professional.getId(),
                professional.getUserId(),
                professional.getBio(),
                professional.getYearsOfExperience(),
                professional.getBaseHourlyRate(),
                specialties,
                professional.getVerificationStatus(),
                professional.getRejectionReason(),
                professional.isGeoActive(),
                professional.getGeoCapturedAt(),
                professional.getGeoAccuracyMeters(),
                professional.getSubscriptionPlanId(),
                professional.getSubscriptionExpiresAt(),
                ratingSummary.averageRating(),
                ratingSummary.reviewCount(),
                professional.getCreatedAt(),
                professional.getUpdatedAt()
        );
    }

    public List<ProfessionalResponse> toResponseList(List<Professional> professionals) {
        return professionals.stream().map(this::toResponse).toList();
    }

    private List<ProfessionalSpecialtyResponse> mapSpecialties(UUID professionalId) {
        var specialties = professionalSpecialtyRepository
                .findAllByProfessionalIdAndDeletedAtIsNullOrderByCreatedAtAsc(professionalId);

        Map<UUID, ServiceCategory> categoriesById = serviceCategoryRepository.findAllById(
                specialties.stream().map(item -> item.getCategoryId()).toList()
        ).stream().collect(Collectors.toMap(ServiceCategory::getId, Function.identity()));

        Map<UUID, ServiceArea> areasById = serviceAreaRepository.findAllById(
                categoriesById.values().stream().map(ServiceCategory::getAreaId).toList()
        ).stream().collect(Collectors.toMap(ServiceArea::getId, Function.identity()));

        return specialties.stream().map(specialty -> {
            ServiceCategory category = categoriesById.get(specialty.getCategoryId());
            ServiceArea area = category != null ? areasById.get(category.getAreaId()) : null;

            return new ProfessionalSpecialtyResponse(
                    specialty.getCategoryId(),
                    category != null ? category.getName() : null,
                    category != null ? category.getAreaId() : null,
                    area != null ? area.getName() : null,
                    specialty.getYearsOfExperience(),
                    specialty.getHourlyRate()
            );
        }).toList();
    }
}
