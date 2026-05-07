package com.allset.api.offering.mapper;

import com.allset.api.offering.domain.PricingType;
import com.allset.api.offering.domain.ProfessionalOffering;
import com.allset.api.offering.dto.ProfessionalOfferingResponse;
import com.allset.api.professional.repository.ProfessionalSpecialtyRepository;
import com.allset.api.review.dto.ReviewRatingSummary;
import com.allset.api.review.service.ReviewSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProfessionalOfferingMapper {

    private final ReviewSummaryService reviewSummaryService;
    private final ProfessionalSpecialtyRepository professionalSpecialtyRepository;

    public ProfessionalOfferingResponse toResponse(ProfessionalOffering offering) {
        ReviewRatingSummary ratingSummary = reviewSummaryService
                .summarizeProfessionalService(offering.getProfessionalId(), offering.getId());

        BigDecimal effectivePrice = resolveEffectivePrice(offering);

        return new ProfessionalOfferingResponse(
                offering.getId(),
                offering.getProfessionalId(),
                offering.getCategoryId(),
                offering.getTitle(),
                offering.getDescription(),
                offering.getPricingType(),
                offering.getPrice(),
                effectivePrice,
                offering.getEstimatedDurationMinutes(),
                offering.isActive(),
                ratingSummary.averageRating(),
                ratingSummary.reviewCount(),
                offering.getCreatedAt()
        );
    }

    public List<ProfessionalOfferingResponse> toResponseList(List<ProfessionalOffering> offerings) {
        return offerings.stream().map(this::toResponse).toList();
    }

    private BigDecimal resolveEffectivePrice(ProfessionalOffering offering) {
        if (offering.getPrice() != null) {
            return offering.getPrice();
        }
        if (offering.getPricingType() == PricingType.hourly) {
            return professionalSpecialtyRepository
                    .findByProfessionalIdAndCategoryIdAndDeletedAtIsNull(
                            offering.getProfessionalId(), offering.getCategoryId())
                    .map(s -> s.getHourlyRate())
                    .orElse(null);
        }
        return null;
    }
}
